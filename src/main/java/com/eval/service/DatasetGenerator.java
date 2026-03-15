package com.eval.service;

import com.eval.model.JavaFunction;
import com.fasterxml.jackson.core.type.TypeReference;
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

@Service
public class DatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerator.class);
    private final AnthropicChatModel chatModel;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${eval.dataset-size:10}")
    private int datasetSize;

    private static final String GENERATOR_SYSTEM = """
        You are a Java expert who creates evaluation datasets for AI systems.
        Generate realistic Java functions and ideal plain-English explanations.

        Rules for the ideal explanation:
          - Written for someone who has never coded before
          - No jargon (no "iterate", "instantiate", "invoke", "return value")
          - One to three sentences maximum
          - Describes WHAT it does, not HOW the code works internally

        Always respond with ONLY a valid JSON array. No markdown, no preamble.
        """;

    public DatasetGenerator(AnthropicChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public List<JavaFunction> generate() {
        log.info("Generating dataset ({} functions)...", datasetSize);

        int simple  = Math.max(1, datasetSize / 3);
        int medium  = Math.max(1, datasetSize / 3);
        int complex = datasetSize - simple - medium;

        String userMsg = """
            Generate exactly %d Java functions:
              - %d simple    (e.g. string reversal, min/max finder, basic maths)
              - %d medium    (e.g. sorting, date calculation, caching)
              - %d complex   (e.g. retry with backoff, LRU cache, recursive traversal)

            Return a JSON array where each element has exactly these fields:
            {
              "name":             "descriptive method name",
              "code":             "the full Java method as a single string",
              "difficulty":       "simple" or "medium" or "complex",
              "idealExplanation": "plain-English explanation following the rules above"
            }
            """.formatted(datasetSize, simple, medium, complex);

        Prompt prompt = new Prompt(List.of(
            new SystemMessage(GENERATOR_SYSTEM),
            new UserMessage(userMsg)
        ));

        String raw = chatModel.call(prompt).getResult().getOutput().getText();
        String json = stripFences(raw);

        try {
            List<JavaFunction> dataset =
                mapper.readValue(json, new TypeReference<List<JavaFunction>>() {});
            log.info("Dataset ready — {} functions generated", dataset.size());
            return dataset;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse dataset JSON.\nRaw:\n" + raw, e);
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
