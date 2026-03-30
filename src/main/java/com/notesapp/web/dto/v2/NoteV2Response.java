package com.notesapp.web.dto.v2;

import com.notesapp.service.document.NoteDocumentV1;

import java.time.Instant;

public record NoteV2Response(
    Long id,
    Long notebookId,
    String notebookName,
    String title,
    String summaryShort,
    String noteType,
    String schemaVersion,
    Long currentRevisionId,
    String editorContent,
    NoteDocumentV1 document,
    Instant createdAt,
    Instant updatedAt
) {
}
