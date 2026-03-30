package com.notesapp.service.document;

public record NoteDocumentMeta(
    Long noteId,
    String title,
    String summaryShort,
    String noteType,
    String schemaVersion,
    Long notebookId,
    Long currentRevisionId
) {
}
