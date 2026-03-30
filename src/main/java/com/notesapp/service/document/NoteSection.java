package com.notesapp.service.document;

public record NoteSection(
    String id,
    String label,
    String kind,
    String contentMarkdown
) {
}
