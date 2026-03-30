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
    boolean exactTitleMatch
) {
}
