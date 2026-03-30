package com.notesapp.service.document;

import java.util.List;

public record NoteDocumentV1(
    NoteDocumentMeta meta,
    List<NoteSection> sections
) {
}
