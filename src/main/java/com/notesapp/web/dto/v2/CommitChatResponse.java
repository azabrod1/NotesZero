package com.notesapp.web.dto.v2;

import com.notesapp.service.aiwrite.ApplyResultV1;
import com.notesapp.service.aiwrite.CommitDebugTraceV1;
import com.notesapp.service.aiwrite.DiffEntry;
import com.notesapp.service.aiwrite.PatchPlanV1;
import com.notesapp.service.aiwrite.ProvenanceV1;
import com.notesapp.service.aiwrite.RoutePlanV1;

import java.util.List;

public record CommitChatResponse(
    Long chatEventId,
    RoutePlanV1 routePlan,
    PatchPlanV1 patchPlan,
    ApplyResultV1 applyResult,
    NoteV2Response updatedNote,
    List<DiffEntry> diff,
    ProvenanceV1 provenance,
    String undoToken,
    String answer,
    CommitDebugTraceV1 debugTrace
) {
}
