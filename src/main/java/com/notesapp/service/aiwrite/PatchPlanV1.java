package com.notesapp.service.aiwrite;

import java.util.List;

public record PatchPlanV1(
    Long targetNotebookId,
    Long targetNoteId,
    String targetNoteType,
    List<PatchOperation> ops,
    boolean fallbackToInbox,
    String plannerPromptVersion
) {
}
