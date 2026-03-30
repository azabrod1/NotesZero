package com.notesapp.service.document;

public final class NoteSectionVisibility {

    private NoteSectionVisibility() {
    }

    public static boolean isHidden(NoteSection section) {
        if (section == null) {
            return false;
        }
        return "inbox".equalsIgnoreCase(section.id()) || "inbox".equalsIgnoreCase(section.kind());
    }
}
