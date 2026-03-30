package com.notesapp.service.aiwrite;

public record DiffEntry(
    String sectionId,
    String label,
    String changeType,
    String beforeText,
    String afterText
) {
}
