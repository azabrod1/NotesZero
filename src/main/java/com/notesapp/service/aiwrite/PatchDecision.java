package com.notesapp.service.aiwrite;

public record PatchDecision(
    PatchPlanV1 patchPlan,
    AiCallTraceV1 trace
) {
}
