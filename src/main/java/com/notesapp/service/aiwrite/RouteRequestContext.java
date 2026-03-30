package com.notesapp.service.aiwrite;

import com.notesapp.service.document.NoteDocumentV1;

import java.util.List;

public record RouteRequestContext(
    String message,
    Long selectedNotebookId,
    Long selectedNoteId,
    List<String> recentMessages,
    RetrievalBundle retrievalBundle,
    NoteDocumentV1 selectedNoteDocument
) {
}
