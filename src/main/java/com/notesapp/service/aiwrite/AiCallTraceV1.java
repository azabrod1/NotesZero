package com.notesapp.service.aiwrite;

import java.util.List;

public record AiCallTraceV1(
    String model,
    String promptVersion,
    Long latencyMs,
    String responseId,
    AiUsageTraceV1 usage,
    List<String> sanitizerOverrides
) {

    public AiCallTraceV1 withAdditionalOverride(String override) {
        if (override == null || override.isBlank()) {
            return this;
        }
        java.util.ArrayList<String> updated = new java.util.ArrayList<>(sanitizerOverrides == null ? List.of() : sanitizerOverrides);
        updated.add(override);
        return new AiCallTraceV1(model, promptVersion, latencyMs, responseId, usage, List.copyOf(updated));
    }
}
