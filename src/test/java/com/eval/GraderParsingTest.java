package com.eval;

import com.eval.model.ExplanationResult;
import com.eval.model.GradeResult;
import com.eval.model.JavaFunction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the grader JSON parsing logic.
 * No API key required — tests the parsing layer in isolation.
 */
class GraderParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // Simulates what the grader LLM returns
    private static final String SAMPLE_GRADER_RESPONSE = """
        {
          "accuracy":     { "score": 5, "reason": "Correctly describes the sorting behaviour." },
          "simplicity":   { "score": 3, "reason": "Uses the word 'iterate' which is jargon." },
          "completeness": { "score": 4, "reason": "Mentions input and output but not edge case." },
          "conciseness":  { "score": 5, "reason": "Two sentences, tight and direct." }
        }
        """;

    @Test
    void parsesAllFourDimensions() throws Exception {
        JsonNode root = mapper.readTree(SAMPLE_GRADER_RESPONSE);

        assertThat(root.path("accuracy")    .path("score").asInt()).isEqualTo(5);
        assertThat(root.path("simplicity")  .path("score").asInt()).isEqualTo(3);
        assertThat(root.path("completeness").path("score").asInt()).isEqualTo(4);
        assertThat(root.path("conciseness") .path("score").asInt()).isEqualTo(5);
    }

    @Test
    void parsesReasonStrings() throws Exception {
        JsonNode root = mapper.readTree(SAMPLE_GRADER_RESPONSE);

        assertThat(root.path("simplicity").path("reason").asText())
            .contains("iterate");
    }

    @Test
    void overallScoreIsAverageOfFourDimensions() {
        JavaFunction fn = new JavaFunction(
            "sortList", "public List<Integer> sortList(...) {...}",
            "simple", "Takes a list of numbers and puts them in order from smallest to largest."
        );
        ExplanationResult er = new ExplanationResult(fn, "Sorts the numbers.");

        GradeResult result = GradeResult.of(
            er, 5, 3, 4, 5,
            "reason", "reason", "reason", "reason",
            4.0
        );

        // (5 + 3 + 4 + 5) / 4 = 4.25
        assertThat(result.overallScore()).isEqualTo(4.25);
        assertThat(result.passed()).isTrue();  // 4.25 >= 4.0
    }

    @Test
    void failsWhenOverallBelowThreshold() {
        JavaFunction fn = new JavaFunction(
            "complexMethod", "public void complexMethod(...) {...}",
            "complex", "Handles retry logic with exponential backoff."
        );
        ExplanationResult er = new ExplanationResult(fn, "This method invokes the retry handler.");

        GradeResult result = GradeResult.of(
            er, 3, 1, 3, 2,
            "reason", "reason", "reason", "reason",
            4.0
        );

        // (3 + 1 + 3 + 2) / 4 = 2.25
        assertThat(result.overallScore()).isEqualTo(2.25);
        assertThat(result.passed()).isFalse();
    }
}
