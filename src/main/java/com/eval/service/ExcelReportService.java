package com.eval.service;

import com.eval.model.EvalReport;
import com.eval.model.GradeResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a formatted Excel report with two sheets:
 *
 *   Sheet 1 — Summary
 *     Run metadata, average scores, pass/fail, weakest dimension
 *
 *   Sheet 2 — Detail
 *     One row per function showing:
 *       - The Java code (prompt sent to LLM)
 *       - The LLM response (explanation produced)
 *       - The ideal explanation (reference)
 *       - Grader scores + reasons for each dimension
 *       - Overall score + Pass/Fail
 */
@Service
public class ExcelReportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelReportService.class);

    @Value("${eval.output-file:eval-report.json}")
    private String outputFile;

    // ── Colours ──────────────────────────────────────────────────────────
    private static final XSSFColor NAVY     = rgb(30,  58,  95);
    private static final XSSFColor WHITE    = rgb(255, 255, 255);
    private static final XSSFColor GREEN_BG = rgb(198, 239, 206);
    private static final XSSFColor GREEN_FG = rgb(0,   97,  0);
    private static final XSSFColor AMBER_BG = rgb(255, 235, 156);
    private static final XSSFColor AMBER_FG = rgb(156, 87,  0);
    private static final XSSFColor RED_BG   = rgb(255, 199, 206);
    private static final XSSFColor RED_FG   = rgb(156, 0,   6);
    private static final XSSFColor ALT_ROW  = rgb(245, 245, 245);
    private static final XSSFColor PROMPT_BG  = rgb(235, 244, 255); // light blue  — prompt
    private static final XSSFColor RESPONSE_BG = rgb(255, 251, 235); // light amber — LLM response
    private static final XSSFColor IDEAL_BG  = rgb(235, 255, 240); // light green — ideal

    // ─────────────────────────────────────────────────────────────────────

    public void save(EvalReport report) {
        String filename = outputFile.replace(".json", "") + "-report.xlsx";

        try (XSSFWorkbook wb = new XSSFWorkbook();
             FileOutputStream out = new FileOutputStream(filename)) {

            buildSummarySheet(wb, report);
            buildDetailSheet(wb, report);

            wb.write(out);
            log.info("Excel report saved: {}", filename);
            System.out.println("\nExcel report saved: " + filename);

        } catch (Exception e) {
            log.error("Failed to write Excel report: {}", e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHEET 1 — SUMMARY
    // ══════════════════════════════════════════════════════════════════════

    private void buildSummarySheet(XSSFWorkbook wb, EvalReport report) {
        XSSFSheet sheet = wb.createSheet("Summary");
        sheet.setColumnWidth(0, chars(32));
        sheet.setColumnWidth(1, chars(20));

        int row = 0;
        row = writeTitle(wb, sheet, row, "Java Explainer — Prompt Evaluation Report");
        row++;

        row = writeSubheader(wb, sheet, row, "Run Details");
        row = writeLabelValue(wb, sheet, row, "Date",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")), false);
        row = writeLabelValue(wb, sheet, row, "Model",    "claude-haiku-4-5-20251001", false);
        row = writeLabelValue(wb, sheet, row, "Functions evaluated",
            String.valueOf(report.totalFunctions()), false);
        row++;

        row = writeSubheader(wb, sheet, row, "Results");
        row = writeLabelValue(wb, sheet, row, "Passed",
            report.passed() + "  (" + pct(report.passRate()) + ")", false);
        row = writeLabelValue(wb, sheet, row, "Failed",
            String.valueOf(report.failed()), false);
        row = writeLabelValue(wb, sheet, row, "Pass threshold", "4.0 / 5.0", false);
        row++;

        row = writeSubheader(wb, sheet, row, "Average Scores (out of 5)");
        String weakest = report.weakestDimension();
        row = writeLabelValue(wb, sheet, row, "Overall",      fmt(report.avgOverall()),      false);
        row = writeLabelValue(wb, sheet, row, "Accuracy",     fmt(report.avgAccuracy()),     "Accuracy".equals(weakest));
        row = writeLabelValue(wb, sheet, row, "Simplicity",   fmt(report.avgSimplicity()),   "Simplicity".equals(weakest));
        row = writeLabelValue(wb, sheet, row, "Completeness", fmt(report.avgCompleteness()), "Completeness".equals(weakest));
        row = writeLabelValue(wb, sheet, row, "Conciseness",  fmt(report.avgConciseness()),  "Conciseness".equals(weakest));
        row++;

        row = writeSubheader(wb, sheet, row, "Action");
        XSSFRow actionRow = sheet.createRow(row);
        XSSFCell actionCell = actionRow.createCell(0);
        actionCell.setCellValue("⚠  Weakest dimension: " + weakest
            + " — focus prompt improvements here");
        actionCell.setCellStyle(warningStyle(wb));
        sheet.addMergedRegion(new CellRangeAddress(row, row, 0, 1));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SHEET 2 — DETAIL
    //
    //  Columns:
    //    A  Function name
    //    B  Difficulty
    //    C  Java Code          ← PROMPT sent to LLM (blue background)
    //    D  LLM Response       ← what the model produced (amber background)
    //    E  Ideal Explanation  ← reference answer (green background)
    //    F  Accuracy score
    //    G  Accuracy reason
    //    H  Simplicity score
    //    I  Simplicity reason
    //    J  Completeness score
    //    K  Completeness reason
    //    L  Conciseness score
    //    M  Conciseness reason
    //    N  Overall
    //    O  Pass / Fail
    // ══════════════════════════════════════════════════════════════════════

    private void buildDetailSheet(XSSFWorkbook wb, EvalReport report) {
        XSSFSheet sheet = wb.createSheet("Detail");

        sheet.setColumnWidth(0,  chars(26));  // A  Function name
        sheet.setColumnWidth(1,  chars(12));  // B  Difficulty
        sheet.setColumnWidth(2,  chars(55));  // C  Java code (prompt)
        sheet.setColumnWidth(3,  chars(50));  // D  LLM response
        sheet.setColumnWidth(4,  chars(45));  // E  Ideal explanation
        sheet.setColumnWidth(5,  chars(12));  // F  Accuracy score
        sheet.setColumnWidth(6,  chars(38));  // G  Accuracy reason
        sheet.setColumnWidth(7,  chars(12));  // H  Simplicity score
        sheet.setColumnWidth(8,  chars(38));  // I  Simplicity reason
        sheet.setColumnWidth(9,  chars(12));  // J  Completeness score
        sheet.setColumnWidth(10, chars(38));  // K  Completeness reason
        sheet.setColumnWidth(11, chars(12));  // L  Conciseness score
        sheet.setColumnWidth(12, chars(38));  // M  Conciseness reason
        sheet.setColumnWidth(13, chars(12));  // N  Overall
        sheet.setColumnWidth(14, chars(12));  // O  Pass/Fail

        // ── Header row ──
        String[] headers = {
            "Function",
            "Difficulty",
            "Java Code  [PROMPT →  LLM]",       // C — blue
            "LLM Response  [← graded]",          // D — amber
            "Ideal Explanation  [reference]",    // E — green
            "Accuracy", "Accuracy Reason",
            "Simplicity", "Simplicity Reason",
            "Completeness", "Completeness Reason",
            "Conciseness", "Conciseness Reason",
            "Overall", "Result"
        };

        XSSFRow headerRow = sheet.createRow(0);
        headerRow.setHeightInPoints(24);
        for (int i = 0; i < headers.length; i++) {
            XSSFCell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            // Colour the three key column headers differently
            if (i == 2) cell.setCellStyle(colourHeaderStyle(wb, rgb(30, 90, 160)));   // prompt — dark blue
            else if (i == 3) cell.setCellStyle(colourHeaderStyle(wb, rgb(160, 100, 0))); // response — dark amber
            else if (i == 4) cell.setCellStyle(colourHeaderStyle(wb, rgb(0, 110, 50)));  // ideal — dark green
            else cell.setCellStyle(headerStyle(wb));
        }

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, headers.length - 1));
        sheet.createFreezePane(0, 1);

        // ── Data rows ──
        List<GradeResult> results = report.results();
        for (int i = 0; i < results.size(); i++) {
            GradeResult r   = results.get(i);
            XSSFRow dataRow = sheet.createRow(i + 1);
            dataRow.setHeightInPoints(80); // tall rows — code + explanations wrap

            boolean alt = i % 2 == 1;

            // A — function name
            writeCell(wb, dataRow, 0, r.explanationResult().function().name(), alt, true, null);

            // B — difficulty (colour coded)
            writeCell(wb, dataRow, 1, r.explanationResult().function().difficulty(),
                alt, false, difficultyStyle(wb, r.explanationResult().function().difficulty()));

            // C — Full prompt = system message + java code
            String fullPrompt = String.join("\n",
                "[SYSTEM PROMPT]",
                "You are a helpful assistant that explains Java code to non-developers.",
                "When given a Java method:",
                "  - Explain what it does in plain, simple English",
                "  - Do NOT use technical jargon",
                "  - Keep it short - 2 to 3 sentences maximum",
                "  - Focus on WHAT it does, not HOW it does it internally",
                "  - Write as if explaining to someone who has never seen code before",
                "Respond with the explanation only. No preamble, no code, no bullet points.",
                "",
                "[USER MESSAGE - Java Code]",
                r.explanationResult().function().code()
            );
            writeCell(wb, dataRow, 2, fullPrompt, false, false, codeStyle(wb));

            // D — LLM response = what was graded
            writeCell(wb, dataRow, 3, r.explanationResult().explanation(),
                false, false, responseStyle(wb));

            // E — ideal explanation = reference
            writeCell(wb, dataRow, 4, r.explanationResult().function().idealExplanation(),
                false, false, idealStyle(wb));

            // F-M — scores + reasons
            writeScoreCell(wb, dataRow, 5,  r.accuracyScore());
            writeCell(wb, dataRow, 6,  r.accuracyReason(),     alt, false, null);
            writeScoreCell(wb, dataRow, 7,  r.simplicityScore());
            writeCell(wb, dataRow, 8,  r.simplicityReason(),   alt, false, null);
            writeScoreCell(wb, dataRow, 9,  r.completenessScore());
            writeCell(wb, dataRow, 10, r.completenessReason(), alt, false, null);
            writeScoreCell(wb, dataRow, 11, r.concisenessScore());
            writeCell(wb, dataRow, 12, r.concisenessReason(),  alt, false, null);

            // N — overall
            writeOverallCell(wb, dataRow, 13, r.overallScore());

            // O — pass/fail
            writePassFailCell(wb, dataRow, 14, r.passed());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CELL WRITERS
    // ══════════════════════════════════════════════════════════════════════

    private void writeCell(XSSFWorkbook wb, XSSFRow row, int col,
                           String value, boolean alt, boolean bold,
                           XSSFCellStyle overrideStyle) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(value != null ? value : "");
        if (overrideStyle != null) {
            cell.setCellStyle(overrideStyle);
            return;
        }
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(alt ? ALT_ROW : WHITE);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setBold(bold);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        cell.setCellStyle(style);
    }

    private void writeScoreCell(XSSFWorkbook wb, XSSFRow row, int col, int score) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(score + " / 5");
        cell.setCellStyle(scoreStyle(wb, score));
    }

    private void writeOverallCell(XSSFWorkbook wb, XSSFRow row, int col, double score) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(String.format("%.2f / 5", score));
        cell.setCellStyle(scoreStyle(wb, (int) Math.round(score)));
    }

    private void writePassFailCell(XSSFWorkbook wb, XSSFRow row, int col, boolean passed) {
        XSSFCell cell = row.createCell(col);
        cell.setCellValue(passed ? "✅ PASS" : "❌ FAIL");
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setFillForegroundColor(passed ? GREEN_BG : RED_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setColor(passed ? GREEN_FG : RED_FG);
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        cell.setCellStyle(style);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  STYLES
    // ══════════════════════════════════════════════════════════════════════

    /** Blue background — Java code (the prompt) */
    private XSSFCellStyle codeStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(PROMPT_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setFontName("Courier New");
        font.setFontHeightInPoints((short) 9);
        style.setFont(font);
        return style;
    }

    /** Amber background — LLM response (what was graded) */
    private XSSFCellStyle responseStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(RESPONSE_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        font.setItalic(true);
        style.setFont(font);
        return style;
    }

    /** Green background — ideal explanation (reference) */
    private XSSFCellStyle idealStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(IDEAL_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        style.setWrapText(true);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle headerStyle(XSSFWorkbook wb) {
        return colourHeaderStyle(wb, NAVY);
    }

    private XSSFCellStyle colourHeaderStyle(XSSFWorkbook wb, XSSFColor bg) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(bg);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setColor(WHITE);
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle scoreStyle(XSSFWorkbook wb, int score) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        if (score >= 4) {
            style.setFillForegroundColor(GREEN_BG);
            font.setColor(GREEN_FG);
        } else if (score == 3) {
            style.setFillForegroundColor(AMBER_BG);
            font.setColor(AMBER_FG);
        } else {
            style.setFillForegroundColor(RED_BG);
            font.setColor(RED_FG);
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle difficultyStyle(XSSFWorkbook wb, String difficulty) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        switch (difficulty.toLowerCase()) {
            case "simple"  -> { style.setFillForegroundColor(GREEN_BG); font.setColor(GREEN_FG); }
            case "medium"  -> { style.setFillForegroundColor(AMBER_BG); font.setColor(AMBER_FG); }
            case "complex" -> { style.setFillForegroundColor(RED_BG);   font.setColor(RED_FG);   }
        }
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setFont(font);
        return style;
    }

    private XSSFCellStyle warningStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(AMBER_BG);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(AMBER_FG);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        return style;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SUMMARY SHEET HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private int writeTitle(XSSFWorkbook wb, XSSFSheet sheet, int rowNum, String title) {
        XSSFRow row = sheet.createRow(rowNum);
        row.setHeightInPoints(28);
        XSSFCell cell = row.createCell(0);
        cell.setCellValue(title);
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(NAVY);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setColor(WHITE);
        font.setBold(true);
        font.setFontHeightInPoints((short) 16);
        style.setFont(font);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 1));
        return rowNum + 1;
    }

    private int writeSubheader(XSSFWorkbook wb, XSSFSheet sheet, int rowNum, String text) {
        XSSFRow row = sheet.createRow(rowNum);
        row.setHeightInPoints(18);
        XSSFCell cell = row.createCell(0);
        cell.setCellValue(text);
        XSSFCellStyle style = wb.createCellStyle();
        style.setFillForegroundColor(rgb(220, 230, 241));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        setBorder(style);
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(NAVY);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        cell.setCellStyle(style);
        sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 1));
        return rowNum + 1;
    }

    private int writeLabelValue(XSSFWorkbook wb, XSSFSheet sheet,
                                int rowNum, String label, String value, boolean isWeakest) {
        XSSFRow row = sheet.createRow(rowNum);

        XSSFCell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        XSSFCellStyle labelStyle = wb.createCellStyle();
        XSSFFont lf = wb.createFont();
        lf.setBold(true);
        lf.setFontHeightInPoints((short) 11);
        labelStyle.setFont(lf);
        setBorder(labelStyle);
        labelCell.setCellStyle(labelStyle);

        XSSFCell valueCell = row.createCell(1);
        valueCell.setCellValue(value);
        XSSFCellStyle valueStyle = wb.createCellStyle();
        setBorder(valueStyle);
        if (isWeakest) {
            valueStyle.setFillForegroundColor(RED_BG);
            valueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont wf = wb.createFont();
            wf.setColor(RED_FG);
            wf.setBold(true);
            valueStyle.setFont(wf);
        }
        valueCell.setCellStyle(valueStyle);
        return rowNum + 1;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════════════════════════════════

    private static XSSFColor rgb(int r, int g, int b) {
        return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
    }

    private static int chars(int n) { return n * 256; }
    private static String pct(double r) { return String.format("%.0f%%", r * 100); }
    private static String fmt(double d) { return String.format("%.2f", d); }

    private void setBorder(XSSFCellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        XSSFColor grey = rgb(200, 200, 200);
        style.setTopBorderColor(grey);
        style.setBottomBorderColor(grey);
        style.setLeftBorderColor(grey);
        style.setRightBorderColor(grey);
    }
}
