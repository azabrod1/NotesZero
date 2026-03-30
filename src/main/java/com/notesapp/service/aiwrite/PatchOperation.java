package com.notesapp.service.aiwrite;

import com.notesapp.service.document.NoteSection;

import java.util.List;

public record PatchOperation(
    PatchOpType op,
    String sectionId,
    String afterSectionId,
    String title,
    String summaryShort,
    String contentMarkdown,
    List<NoteSection> sections
) {
}
