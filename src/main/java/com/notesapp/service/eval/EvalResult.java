package com.notesapp.service.eval;

import com.notesapp.service.aiwrite.RoutePlanV1;
import com.notesapp.service.aiwrite.RetrievalBundle;

import java.util.List;

public record EvalResult(
    String caseId,
    String message,
    GoldSetCase.GoldSetExpected expected,
    RoutePlanV1 actualRoute,
    RetrievalBundle retrievalBundle,
    boolean intentCorrect,
    boolean notebookCorrect,
    boolean noteCorrect,
    boolean candidateRecallAt5,
    boolean candidateRecallAt8,
    boolean notebookRecallAt3,
    boolean wrongTarget,
    List<String> errors
) {
}
