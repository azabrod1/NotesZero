package com.notesapp.service.aiwrite;

import java.util.List;

public record RoutePlanV1(
    RouteIntent intent,
    Long targetNotebookId,
    Long targetNoteId,
    String targetNoteType,
    double confidence,
    List<String> reasonCodes,
    RouteStrategy strategy,
    String answer
) {
}
