package com.eval.model;

import java.util.List;

/**
 * Aggregated report printed and saved at the end of the eval run.
 */
public record EvalReport(

    int totalFunctions,
    int passed,
    int failed,
    double passRate,           // 0.0 – 1.0

    double avgOverall,
    double avgAccuracy,
    double avgSimplicity,
    double avgCompleteness,
    double avgConciseness,

    String weakestDimension,   // the dimension with the lowest average

    List<GradeResult> results  // full detail for every function

) {
    public String summary() {
        return """
            ═══════════════════════════════════════════════
             EVAL REPORT — Java Function Explainer
            ═══════════════════════════════════════════════
             Functions evaluated : %d
             Passed (score ≥ %.1f): %d  (%.0f%%)
             Failed              : %d

             Average scores (out of 5):
               Overall      : %.2f
               Accuracy     : %.2f
               Simplicity   : %.2f
               Completeness : %.2f
               Conciseness  : %.2f

             ⚠  Weakest dimension : %s
            ═══════════════════════════════════════════════
            """.formatted(
                totalFunctions,
                4.0,              // passThreshold — hardcoded for display
                passed, passRate * 100,
                failed,
                avgOverall,
                avgAccuracy, avgSimplicity, avgCompleteness, avgConciseness,
                weakestDimension
            );
    }
}
