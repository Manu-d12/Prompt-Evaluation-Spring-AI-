package com.eval.runner;

import com.eval.model.EvalReport;
import com.eval.model.ExplanationResult;
import com.eval.model.GradeResult;
import com.eval.model.JavaFunction;
import com.eval.service.DatasetGenerator;
import com.eval.service.ExcelReportService;
import com.eval.service.ExplainerService;
import com.eval.service.GraderService;
import com.eval.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EvalRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

    private final DatasetGenerator   datasetGenerator;
    private final ExplainerService   explainerService;
    private final GraderService      graderService;
    private final ReportService      reportService;
    private final ExcelReportService excelReportService;

    public EvalRunner(DatasetGenerator   datasetGenerator,
                      ExplainerService   explainerService,
                      GraderService      graderService,
                      ReportService      reportService,
                      ExcelReportService excelReportService) {
        this.datasetGenerator   = datasetGenerator;
        this.explainerService   = explainerService;
        this.graderService      = graderService;
        this.reportService      = reportService;
        this.excelReportService = excelReportService;
    }

    @Override
    public void run(String... args) {
        System.out.println("""
            
            ╔══════════════════════════════════════════════╗
            ║   Java Explainer — Prompt Evaluation Suite   ║
            ╚══════════════════════════════════════════════╝
            """);

        // Step 1 — Generate dataset
        System.out.println("Step 1 / 3  Generating dataset...");
        List<JavaFunction> dataset = datasetGenerator.generate();
        System.out.printf("            %d functions ready ✓%n%n", dataset.size());

        // Step 2 — Run explainer prompt
        System.out.println("Step 2 / 3  Running explainer prompt...");
        List<ExplanationResult> explanations = new ArrayList<>();
        for (int i = 0; i < dataset.size(); i++) {
            JavaFunction fn = dataset.get(i);
            System.out.printf("            [%d/%d] %s [%s]%n",
                i + 1, dataset.size(), fn.name(), fn.difficulty());
            explanations.add(explainerService.explain(fn));
        }
        System.out.println("            Done ✓\n");

        // Step 3 — Grade each explanation
        System.out.println("Step 3 / 3  Grading explanations (LLM-as-judge)...");
        List<GradeResult> grades = new ArrayList<>();
        for (int i = 0; i < explanations.size(); i++) {
            ExplanationResult er = explanations.get(i);
            System.out.printf("            [%d/%d] Grading: %s%n",
                i + 1, explanations.size(), er.function().name());
            grades.add(graderService.grade(er));
        }
        System.out.println("            Done ✓\n");

        // Build + save reports
        EvalReport report = reportService.build(grades);
        reportService.print(report);     // console
        reportService.save(report);      // JSON (kept as backup)
        excelReportService.save(report); // Excel — the readable one
    }
}
