package com.notesapp.service.aiwrite;

public record NotebookCandidate(
    Long notebookId,
    String name,
    String routingSummary,
    double score
) {
}
