package com.eval.model;

/**
 * One item in the evaluation dataset.
 *
 * difficulty: "simple" | "medium" | "complex"
 * idealExplanation: what a perfect plain-English answer looks like
 *                   (used by the grader as a reference)
 */
public record JavaFunction(
    String name,
    String code,
    String difficulty,
    String idealExplanation
) {}
