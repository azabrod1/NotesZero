package com.notesapp.service.aiwrite;

import java.util.List;

public record RetrievalBundle(
    List<NotebookCandidate> notebookCandidates,
    List<NoteCandidate> noteCandidates
) {
}
