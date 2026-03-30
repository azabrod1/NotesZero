package com.notesapp.service.aiwrite;

public record CommitDebugTraceV1(
    RetrievalDebugTraceV1 retrieval,
    AiCallTraceV1 route,
    AiCallTraceV1 patch,
    Long mutationLatencyMs
) {
}
