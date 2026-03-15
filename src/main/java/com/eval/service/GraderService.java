package com.eval.service;

import com.eval.model.ExplanationResult;
import com.eval.model.GradeResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LLM-as-judge grader.
 * Scores each explanation on four dimensions independently (1-5 each).
 */
@Service
public class GraderService {

    private static final Logger log = LoggerFactory.getLogger(GraderService.class);
    private final AnthropicChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${eval.pass-threshold:4.0}")
    private double passThreshold;

    private static final String GRADER_SYSTEM = """
        You are an expert evaluator for AI-generated code explanations.

        You will be given:
          - A Java function (source code)
          - An ideal explanation (reference answer)
          - An actual explanation (what the AI produced — grade this)

        Score the ACTUAL explanation on four dimensions, each 1-5.
        The ideal is a reference only — different wording can still score 5/5.

        SCORING RUBRICS:

        ACCURACY (correctly describes what the code does)
          5 = Fully correct. No mistakes.
          3 = Mostly correct, one minor inaccuracy.
          1 = Wrong or seriously misleading.

        SIMPLICITY (no jargon, understandable by a non-developer)
          5 = Zero technical terms. A 10-year-old would understand it.
          3 = Mostly simple but uses 1-2 technical words.
          1 = Heavy jargon. A non-developer would not understand it.

        COMPLETENESS (covers all key parts of the function)
          5 = Mentions all important inputs, outputs, and purpose.
          3 = Covers main point but misses a notable detail.
          1 = Vague or only covers part of what the function does.

        CONCISENESS (not too long or padded)
          5 = 1-3 sentences. Tight and direct.
          3 = 4-6 sentences. Could be shorter.
          1 = 7+ sentences or heavily padded.

        Respond ONLY with this exact JSON — no other text:
        {
          "accuracy":     { "score": <1-5>, "reason": "<one sentence>" },
          "simplicity":   { "score": <1-5>, "reason": "<one sentence>" },
          "completeness": { "score": <1-5>, "reason": "<one sentence>" },
          "conciseness":  { "score": <1-5>, "reason": "<one sentence>" }
        }
        """;

    public GraderService(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public GradeResult grade(ExplanationResult result) {
        log.debug("Grading: {}", result.function().name());

        String userMsg = """
            JAVA FUNCTION:
            %s

            IDEAL EXPLANATION (reference):
            %s

            ACTUAL EXPLANATION (grade this):
            %s
            """.formatted(
                result.function().code(),
                result.function().idealExplanation(),
                result.explanation()
            );

        Prompt prompt = new Prompt(List.of(
            new SystemMessage(GRADER_SYSTEM),
            new UserMessage(userMsg)
        ));

        String raw = chatModel.call(prompt)
            .getResult().getOutput().getText();

        return parseGrade(result, stripFences(raw));
    }

    private GradeResult parseGrade(ExplanationResult result, String json) {
        try {
            JsonNode root = mapper.readTree(json);

            int accuracy     = root.path("accuracy")    .path("score").asInt(3);
            int simplicity   = root.path("simplicity")  .path("score").asInt(3);
            int completeness = root.path("completeness").path("score").asInt(3);
            int conciseness  = root.path("conciseness") .path("score").asInt(3);

            String accReason  = root.path("accuracy")    .path("reason").asText("");
            String simReason  = root.path("simplicity")  .path("reason").asText("");
            String comReason  = root.path("completeness").path("reason").asText("");
            String conReason  = root.path("conciseness") .path("reason").asText("");

            return GradeResult.of(result,
                accuracy, simplicity, completeness, conciseness,
                accReason, simReason, comReason, conReason,
                passThreshold);

        } catch (Exception e) {
            log.error("Failed to parse grader response for '{}': {}",
                result.function().name(), json);
            return GradeResult.of(result, 1, 1, 1, 1,
                "parse error", "parse error", "parse error", "parse error",
                passThreshold);
        }
    }

    private String stripFences(String raw) {
        String s = raw.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("```[a-z]*\\n?", "");
            if (s.endsWith("```")) s = s.substring(0, s.lastIndexOf("```"));
        }
        return s.strip();
    }
}
