package com.notesapp.service.aiwrite;

import java.util.List;

public record ProvenanceV1(
    Long chatEventId,
    String providerName,
    String routerModel,
    String plannerModel,
    String routerPromptVersion,
    String plannerPromptVersion,
    double routeConfidence,
    List<String> reasonCodes
) {
}
