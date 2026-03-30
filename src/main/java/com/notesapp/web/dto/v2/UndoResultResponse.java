package com.notesapp.web.dto.v2;

import com.notesapp.service.aiwrite.ApplyResultV1;
import com.notesapp.service.aiwrite.DiffEntry;

import java.util.List;

public record UndoResultResponse(
    ApplyResultV1 applyResult,
    NoteV2Response updatedNote,
    List<DiffEntry> diff,
    String undoToken
) {
}
