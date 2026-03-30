package com.notesapp.service.aiwrite;

import com.notesapp.service.document.NoteDocumentV1;

public record PatchRequestContext(
    String message,
    RoutePlanV1 routePlan,
    NoteDocumentV1 targetDocument
) {
}
