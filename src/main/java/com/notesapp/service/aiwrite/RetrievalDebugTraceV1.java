package com.notesapp.service.aiwrite;

import java.util.List;

public record RetrievalDebugTraceV1(
    Long selectedNotebookId,
    Long selectedNoteId,
    Long latencyMs,
    Double topNotebookGap,
    Double topNoteGap,
    List<NotebookRetrievalCandidateTraceV1> notebookCandidates,
    List<NoteRetrievalCandidateTraceV1> noteCandidates
) {
}
