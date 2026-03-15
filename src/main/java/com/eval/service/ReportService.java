package com.eval.service;

import com.eval.model.EvalReport;
import com.eval.model.GradeResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Aggregates individual GradeResults into a final EvalReport.
 * Prints the report to console and saves full detail as JSON.
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private final ObjectMapper mapper = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @Value("${eval.pass-threshold:4.0}")
    private double passThreshold;

    @Value("${eval.output-file:eval-report.json}")
    private String outputFile;

    // ─────────────────────────────────────────────────────────────────────────

    public EvalReport build(List<GradeResult> results) {

        int total  = results.size();
        int passed = (int) results.stream().filter(GradeResult::passed).count();
        int failed = total - passed;

        double avgOverall      = avg(results, r -> r.overallScore());
        double avgAccuracy     = avgInt(results, GradeResult::accuracyScore);
        double avgSimplicity   = avgInt(results, GradeResult::simplicityScore);
        double avgCompleteness = avgInt(results, GradeResult::completenessScore);
        double avgConciseness  = avgInt(results, GradeResult::concisenessScore);

        String weakest = findWeakest(Map.of(
            "Accuracy",     avgAccuracy,
            "Simplicity",   avgSimplicity,
            "Completeness", avgCompleteness,
            "Conciseness",  avgConciseness
        ));

        return new EvalReport(
            total, passed, failed,
            (double) passed / total,
            avgOverall,
            avgAccuracy, avgSimplicity, avgCompleteness, avgConciseness,
            weakest,
            results
        );
    }

    // ─────────────────────────────────────────────────────────────────────────

    public void print(EvalReport report) {
        // Print per-function detail
        System.out.println("\n── Per-function results ─────────────────────────────────");
        report.results().forEach(this::printResult);

        // Print summary
        System.out.println(report.summary());
    }

    public void save(EvalReport report) {
        try {
            mapper.writeValue(new File(outputFile), report);
            log.info("Full report saved to: {}", outputFile);
        } catch (Exception e) {
            log.error("Could not save report: {}", e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private void printResult(GradeResult r) {
        String status = r.passed() ? "✅ PASS" : "❌ FAIL";
        System.out.printf("""
            
            %s  %s  [%s]
            ─────────────────────────────────────────────────
              Actual : %s
            
              Accuracy     %d/5  %s
              Simplicity   %d/5  %s
              Completeness %d/5  %s
              Conciseness  %d/5  %s
            
              Overall: %.2f/5
            """,
            status,
            r.explanationResult().function().name(),
            r.explanationResult().function().difficulty(),
            r.explanationResult().explanation(),
            r.accuracyScore(),     r.accuracyReason(),
            r.simplicityScore(),   r.simplicityReason(),
            r.completenessScore(), r.completenessReason(),
            r.concisenessScore(),  r.concisenessReason(),
            r.overallScore()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────

    private double avg(List<GradeResult> results,
                       java.util.function.ToDoubleFunction<GradeResult> fn) {
        return results.stream().mapToDouble(fn).average().orElse(0);
    }

    private double avgInt(List<GradeResult> results,
                          java.util.function.ToIntFunction<GradeResult> fn) {
        return results.stream().mapToInt(fn).average().orElse(0);
    }

    private String findWeakest(Map<String, Double> scores) {
        return scores.entrySet().stream()
            .min(Comparator.comparingDouble(Map.Entry::getValue))
            .map(Map.Entry::getKey)
            .orElse("Unknown");
    }
}
