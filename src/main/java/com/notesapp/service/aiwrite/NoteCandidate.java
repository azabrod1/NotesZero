package com.notesapp.service.aiwrite;

import java.time.Instant;
import java.util.List;

public record NoteCandidate(
    Long noteId,
    Long notebookId,
    String title,
    String summaryShort,
    String noteType,
    List<String> sectionLabels,
    String topSectionSnippet,
    Instant updatedAt,
    double score,
    boolean exactTitleMatch,
    String scopeSummary,
    List<String> entityTags,
    String activityStatus
) {
    /**
     * Backward-compatible constructor without index-enriched fields.
     */
    public NoteCandidate(Long noteId, Long notebookId, String title, String summaryShort,
                         String noteType, List<String> sectionLabels, String topSectionSnippet,
                         Instant updatedAt, double score, boolean exactTitleMatch) {
        this(noteId, notebookId, title, summaryShort, noteType, sectionLabels, topSectionSnippet,
             updatedAt, score, exactTitleMatch, null, null, null);
    }
}
