package com.notesapp.service.routing;

import java.util.List;

public record NotebookProfileExtraction(
    String scopeSummary,
    List<String> entityTags,
    List<String> preferredFamilies
) {
}
