package com.notesapp.service.aiwrite;

import java.util.List;

public record NotebookCandidate(
    Long notebookId,
    String name,
    String routingSummary,
    double score,
    List<String> entityTags
) {
    /**
     * Backward-compatible constructor without entityTags.
     */
    public NotebookCandidate(Long notebookId, String name, String routingSummary, double score) {
        this(notebookId, name, routingSummary, score, null);
    }
}
