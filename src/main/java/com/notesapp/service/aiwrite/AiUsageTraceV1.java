package com.notesapp.service.aiwrite;

public record AiUsageTraceV1(
    Integer inputTokens,
    Integer outputTokens,
    Integer totalTokens,
    Integer reasoningTokens,
    Integer cachedInputTokens,
    Double estimatedCostUsd
) {
}
