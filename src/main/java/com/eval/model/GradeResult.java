package com.eval.model;

/**
 * Scores produced by the LLM-as-judge grader for one explanation.
 * Each dimension is scored 1–5.
 * overall = average of the four dimensions.
 */
public record GradeResult(

    // the item being graded
    ExplanationResult explanationResult,

    // four independent dimension scores
    int accuracyScore,
    int simplicityScore,
    int completenessScore,
    int concisenessScore,

    // one-sentence reason per dimension
    String accuracyReason,
    String simplicityReason,
    String completenessReason,
    String concisenessReason,

    // computed average
    double overallScore,

    // PASS / FAIL based on threshold in application.yml
    boolean passed
) {
    /** Convenience constructor — computes overall automatically. */
    public static GradeResult of(
        ExplanationResult result,
        int accuracy, int simplicity, int completeness, int conciseness,
        String accuracyReason, String simplicityReason,
        String completenessReason, String concisenessReason,
        double passThreshold
    ) {
        double overall = (accuracy + simplicity + completeness + conciseness) / 4.0;
        return new GradeResult(
            result,
            accuracy, simplicity, completeness, conciseness,
            accuracyReason, simplicityReason, completenessReason, concisenessReason,
            overall,
            overall >= passThreshold
        );
    }
}
