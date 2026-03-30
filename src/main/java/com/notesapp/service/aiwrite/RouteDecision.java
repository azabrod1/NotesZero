package com.notesapp.service.aiwrite;

public record RouteDecision(
    RoutePlanV1 routePlan,
    AiCallTraceV1 trace
) {
}
