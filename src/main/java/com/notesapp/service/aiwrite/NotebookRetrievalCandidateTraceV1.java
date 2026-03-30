package com.notesapp.service.aiwrite;

public record NotebookRetrievalCandidateTraceV1(
    Long notebookId,
    String name,
    String routingSummary,
    double score
) {
}
