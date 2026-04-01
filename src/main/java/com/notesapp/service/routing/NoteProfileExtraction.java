package com.notesapp.service.routing;

import java.util.List;

public record NoteProfileExtraction(
    String scopeSummary,
    List<String> entityTags,
    List<String> aliases,
    String activityStatus
) {
}
