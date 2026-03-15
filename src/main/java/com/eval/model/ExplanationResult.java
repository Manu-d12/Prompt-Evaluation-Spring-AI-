package com.eval.model;

/**
 * The raw output produced by the explainer prompt under test.
 */
public record ExplanationResult(
    JavaFunction function,   // the input
    String explanation       // what the LLM said
) {}
