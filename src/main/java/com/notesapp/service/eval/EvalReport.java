package com.notesapp.service.eval;

import java.time.Instant;
import java.util.List;

public record EvalReport(
    Instant runAt,
    int totalCases,
    int intentCorrect,
    int notebookCorrect,
    int noteCorrect,
    int candidateRecallAt5,
    int candidateRecallAt8,
    int notebookRecallAt3,
    int wrongTargetWrites,
    double intentAccuracy,
    double notebookAccuracy,
    double candidateRecallAt5Rate,
    double wrongTargetRate,
    List<EvalResult> results
) {
    public static EvalReport fromResults(List<EvalResult> results) {
        int total = results.size();
        int intentOk = (int) results.stream().filter(EvalResult::intentCorrect).count();
        int nbOk = (int) results.stream().filter(EvalResult::notebookCorrect).count();
        int noteOk = (int) results.stream().filter(EvalResult::noteCorrect).count();
        int recall5 = (int) results.stream().filter(EvalResult::candidateRecallAt5).count();
        int recall8 = (int) results.stream().filter(EvalResult::candidateRecallAt8).count();
        int nbRecall3 = (int) results.stream().filter(EvalResult::notebookRecallAt3).count();
        int wrongTarget = (int) results.stream().filter(EvalResult::wrongTarget).count();
        return new EvalReport(
            Instant.now(),
            total,
            intentOk,
            nbOk,
            noteOk,
            recall5,
            recall8,
            nbRecall3,
            wrongTarget,
            total == 0 ? 0.0 : (double) intentOk / total,
            total == 0 ? 0.0 : (double) nbOk / total,
            total == 0 ? 0.0 : (double) recall5 / total,
            total == 0 ? 0.0 : (double) wrongTarget / total,
            results
        );
    }
}
