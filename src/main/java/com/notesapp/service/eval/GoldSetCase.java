package com.notesapp.service.eval;

import java.util.List;

public record GoldSetCase(
    String id,
    String message,
    Long selectedNotebookId,
    Long selectedNoteId,
    GoldSetExpected expected
) {
    public record GoldSetExpected(
        String notebookName,
        String noteTitle,
        String intent,
        String section,
        boolean shouldCreateNew,
        boolean shouldClarify,
        String mergeExpectation,
        String noteType,
        List<String> tags
    ) {
    }
}
