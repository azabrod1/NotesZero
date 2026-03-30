package com.notesapp.service.aiwrite;

import java.util.List;

public record ApplyResultV1(
    Long noteId,
    Long notebookId,
    Long beforeRevisionId,
    Long afterRevisionId,
    String outcome,
    List<String> changedSectionIds
) {
}
