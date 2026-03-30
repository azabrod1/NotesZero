package com.notesapp.web.dto.v2;

import java.time.Instant;

public record NoteSummaryV2Response(
    Long id,
    Long notebookId,
    String title,
    String summaryShort,
    String noteType,
    String schemaVersion,
    Long currentRevisionId,
    Instant createdAt,
    Instant updatedAt
) {
}
