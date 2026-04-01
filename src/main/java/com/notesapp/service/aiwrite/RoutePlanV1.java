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
    String answer,
    List<Long> needContextNoteIds
) {
    /**
     * Backward-compatible constructor without needContextNoteIds.
     */
    public RoutePlanV1(RouteIntent intent, Long targetNotebookId, Long targetNoteId,
                       String targetNoteType, double confidence, List<String> reasonCodes,
                       RouteStrategy strategy, String answer) {
        this(intent, targetNotebookId, targetNoteId, targetNoteType, confidence,
             reasonCodes, strategy, answer, null);
    }
}
