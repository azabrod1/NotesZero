package com.notesapp.web.dto.v2;

import java.time.Instant;

public record NotebookV2Response(
    Long id,
    String name,
    String description,
    String routingSummary,
    Instant createdAt
) {
}
