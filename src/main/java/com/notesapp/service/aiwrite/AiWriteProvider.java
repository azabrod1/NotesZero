package com.notesapp.service.aiwrite;

import com.notesapp.service.document.NoteDocumentV1;

public interface AiWriteProvider {

    default RoutePlanV1 route(RouteRequestContext context) {
        return routeWithTrace(context).routePlan();
    }

    default PatchPlanV1 plan(PatchRequestContext context) {
        return planWithTrace(context).patchPlan();
    }

    default NanoTriageResult triage(String message, java.util.List<String> recentMessages) {
        return new NanoTriageResult(NanoTriageResult.TriageType.WRITE, null);
    }

    RouteDecision routeWithTrace(RouteRequestContext context);

    PatchDecision planWithTrace(PatchRequestContext context);

    NoteSummaryResult summarize(NoteDocumentV1 document);

    String providerName();

    String routerModel();

    String plannerModel();

    String routerPromptVersion();

    String plannerPromptVersion();
}
