package com.notesapp.web.dto.v2;

import java.time.Instant;

public record RevisionHistoryEntryResponse(
    Long revisionId,
    Long revisionNumber,
    String title,
    String summaryShort,
    Long sourceChatEventId,
    Instant createdAt
) {
}
