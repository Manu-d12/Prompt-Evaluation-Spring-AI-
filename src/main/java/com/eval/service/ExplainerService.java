package com.eval.service;

import com.eval.model.ExplanationResult;
import com.eval.model.JavaFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * THE PROMPT UNDER TEST.
 *
 * This is what you iterate on when scores are low.
 * V1 is active. Uncomment V2 and re-run to compare scores.
 */
@Service
public class ExplainerService {

    private static final Logger log = LoggerFactory.getLogger(ExplainerService.class);
    private final AnthropicChatModel chatModel;

    // ── Prompt V1 — baseline ──────────────────────────────────────────────
    private static final String EXPLAINER_SYSTEM = """
        You are a helpful assistant that explains Java code to non-developers.

        When given a Java method:
          - Explain what it does in plain, simple English
          - Do NOT use technical jargon
          - Keep it short — 2 to 3 sentences maximum
          - Focus on WHAT it does, not HOW it does it internally
          - Write as if explaining to someone who has never seen code before

        Respond with the explanation only. No preamble, no code, no bullet points.
        """;

    // ── Prompt V2 — uncomment to test, compare scores against V1 ─────────
    //
    // private static final String EXPLAINER_SYSTEM = """
    //     Explain Java code to a complete beginner — imagine a 10-year-old asking
    //     "what does this do?"
    //
    //     Rules:
    //       - 1 to 2 sentences only
    //       - Forbidden words: iterate, invoke, instantiate, return, parameter, method, call
    //       - Say what it produces or achieves, not what steps it takes
    //       - Never start with "This function" or "This method"
    //
    //     Example good: "Takes a list of names and finds the longest one."
    //     Example bad:  "This method iterates over the list and returns the element
    //                    with maximum string length."
    //
    //     Reply with only your explanation.
    //     """;

    public ExplainerService(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public ExplanationResult explain(JavaFunction function) {
        log.debug("Explaining: {} [{}]", function.name(), function.difficulty());

        Prompt prompt = new Prompt(List.of(
            new SystemMessage(EXPLAINER_SYSTEM),
            new UserMessage(function.code())
        ));

        String explanation = chatModel.call(prompt)
            .getResult().getOutput().getText().strip();

        return new ExplanationResult(function, explanation);
    }
}
