package com.notesapp.service.aiwrite;

import com.notesapp.config.AiProperties;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MockAiWriteProvider implements AiWriteProvider {

    private final AiProperties aiProperties;

    public MockAiWriteProvider(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public RouteDecision routeWithTrace(RouteRequestContext context) {
        String message = normalize(context.message());
        List<String> reasons = new ArrayList<>();

        if (looksLikeQuestion(message) && !looksLikeEditRequest(message)) {
            reasons.add("question_pattern");
            return new RouteDecision(new RoutePlanV1(
                RouteIntent.ANSWER_ONLY,
                pickNotebookId(context),
                context.selectedNoteId(),
                inferNoteType(context),
                0.84,
                reasons,
                RouteStrategy.ANSWER_ONLY,
                null
            ), emptyTrace(routerModel(), routerPromptVersion()));
        }

        if (context.selectedNoteId() != null && refersToCurrentNote(message)) {
            reasons.add("selected_note_hint");
            if (looksLikeRewriteRequest(message)) {
                reasons.add("rewrite_request");
            }
            return new RouteDecision(new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                pickNotebookId(context),
                context.selectedNoteId(),
                inferNoteType(context),
                0.86,
                reasons,
                RouteStrategy.DIRECT_APPLY,
                null
            ), emptyTrace(routerModel(), routerPromptVersion()));
        }

        if (context.selectedNoteId() == null && looksAmbiguousCapture(message)) {
            reasons.add("ambiguous_capture");
            reasons.add(context.selectedNotebookId() != null ? "selected_notebook_hint" : "retrieval_notebook_match");
            return new RouteDecision(new RoutePlanV1(
                RouteIntent.CREATE_NOTE,
                pickNotebookId(context),
                null,
                CanonicalNoteTemplates.GENERIC_NOTE,
                0.40,
                reasons,
                RouteStrategy.NOTEBOOK_INBOX,
                null
            ), emptyTrace(routerModel(), routerPromptVersion()));
        }

        if (!context.retrievalBundle().noteCandidates().isEmpty()
            && context.retrievalBundle().noteCandidates().get(0).score() >= 0.64) {
            NoteCandidate candidate = context.retrievalBundle().noteCandidates().get(0);
            reasons.add("retrieval_note_match");
            return new RouteDecision(new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                candidate.notebookId(),
                candidate.noteId(),
                candidate.noteType(),
                candidate.score(),
                reasons,
                candidate.score() >= aiProperties.getRouteAutoApplyThreshold()
                    ? RouteStrategy.DIRECT_APPLY
                    : RouteStrategy.NOTE_INBOX,
                null
            ), emptyTrace(routerModel(), routerPromptVersion()));
        }

        Long notebookId = pickNotebookId(context);
        double notebookConfidence = context.retrievalBundle().notebookCandidates().isEmpty()
            ? 0.40
            : context.retrievalBundle().notebookCandidates().get(0).score();
        if (looksAmbiguousCapture(message)) {
            notebookConfidence = Math.min(notebookConfidence, 0.40);
            reasons.add("ambiguous_capture");
        } else if (context.selectedNotebookId() != null) {
            notebookConfidence = Math.max(notebookConfidence, aiProperties.getRouteAutoApplyThreshold() + 0.08);
            reasons.add("selected_notebook_direct_create");
        }
        reasons.add(context.selectedNotebookId() != null ? "selected_notebook_hint" : "retrieval_notebook_match");
        return new RouteDecision(new RoutePlanV1(
            RouteIntent.CREATE_NOTE,
            notebookId,
            null,
            inferCreateNoteType(message),
            notebookConfidence,
            reasons,
            notebookConfidence >= aiProperties.getRouteAutoApplyThreshold()
                ? RouteStrategy.DIRECT_APPLY
                : RouteStrategy.NOTEBOOK_INBOX,
            null
        ), emptyTrace(routerModel(), routerPromptVersion()));
    }

    @Override
    public PatchDecision planWithTrace(PatchRequestContext context) {
        if (context.routePlan().intent() == RouteIntent.ANSWER_ONLY || context.routePlan().intent() == RouteIntent.CLARIFY) {
            return new PatchDecision(new PatchPlanV1(
                context.routePlan().targetNotebookId(),
                context.routePlan().targetNoteId(),
                context.routePlan().targetNoteType(),
                List.of(),
                false,
                plannerPromptVersion()
            ), emptyTrace(plannerModel(), plannerPromptVersion()));
        }

        if (context.routePlan().intent() == RouteIntent.CREATE_NOTE) {
            String title = suggestTitle(context.message());
            String sectionId = preferredSection(context.message(), context.routePlan().targetNoteType());
            PatchOperation createOp = new PatchOperation(
                PatchOpType.CREATE_NOTE,
                null,
                null,
                title,
                summarizeText(context.message()),
                null,
                null
            );
            PatchOperation contentOp = new PatchOperation(
                PatchOpType.APPEND_SECTION_CONTENT,
                fallbackSection(sectionId, context.routePlan().strategy(), context.routePlan().targetNoteType()),
                null,
                null,
                null,
                formatForSection(context.message(), fallbackSection(sectionId, context.routePlan().strategy(), context.routePlan().targetNoteType())),
                null
            );
            return new PatchDecision(new PatchPlanV1(
                context.routePlan().targetNotebookId(),
                null,
                context.routePlan().targetNoteType(),
                List.of(createOp, contentOp),
                context.routePlan().strategy() != RouteStrategy.DIRECT_APPLY,
                plannerPromptVersion()
            ), emptyTrace(plannerModel(), plannerPromptVersion()));
        }

        if (looksLikeRewriteRequest(context.message())) {
            List<NoteSection> rewritten = rewriteSections(context.targetDocument(), context.message());
            return new PatchDecision(new PatchPlanV1(
                context.routePlan().targetNotebookId(),
                context.routePlan().targetNoteId(),
                context.routePlan().targetNoteType(),
                List.of(
                    new PatchOperation(
                        PatchOpType.REPLACE_NOTE_OUTLINE,
                        null,
                        null,
                        suggestTitle(context.message(), context.targetDocument()),
                        summarizeDocument(rewritten),
                        null,
                        rewritten
                    )
                ),
                false,
                plannerPromptVersion()
            ), emptyTrace(plannerModel(), plannerPromptVersion()));
        }

        String sectionId = preferredSection(context.message(), context.routePlan().targetNoteType());
        String resolvedSectionId = fallbackSection(sectionId, context.routePlan().strategy(), context.routePlan().targetNoteType());
        return new PatchDecision(new PatchPlanV1(
            context.routePlan().targetNotebookId(),
            context.routePlan().targetNoteId(),
            context.routePlan().targetNoteType(),
            List.of(
                new PatchOperation(
                    PatchOpType.APPEND_SECTION_CONTENT,
                    resolvedSectionId,
                    null,
                    null,
                    summarizeText(context.message()),
                    formatForSection(context.message(), resolvedSectionId),
                    null
                )
            ),
            context.routePlan().strategy() != RouteStrategy.DIRECT_APPLY,
            plannerPromptVersion()
        ), emptyTrace(plannerModel(), plannerPromptVersion()));
    }

    @Override
    public NoteSummaryResult summarize(NoteDocumentV1 document) {
        String summary = summarizeDocument(document.sections());
        return new NoteSummaryResult(summary, summary);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    @Override
    public String routerModel() {
        return aiProperties.getRouterModel();
    }

    @Override
    public String plannerModel() {
        return aiProperties.getPlannerModel();
    }

    @Override
    public String routerPromptVersion() {
        return aiProperties.getRouterPromptId().isBlank() ? "local-router-v1" : aiProperties.getRouterPromptId();
    }

    @Override
    public String plannerPromptVersion() {
        return aiProperties.getPlannerPromptId().isBlank() ? "local-planner-v1" : aiProperties.getPlannerPromptId();
    }

    private Long pickNotebookId(RouteRequestContext context) {
        if (context.selectedNotebookId() != null) {
            return context.selectedNotebookId();
        }
        if (!context.retrievalBundle().notebookCandidates().isEmpty()) {
            return context.retrievalBundle().notebookCandidates().get(0).notebookId();
        }
        return null;
    }

    private String inferNoteType(RouteRequestContext context) {
        if (context.selectedNoteDocument() != null) {
            return context.selectedNoteDocument().meta().noteType();
        }
        if (!context.retrievalBundle().noteCandidates().isEmpty()) {
            return context.retrievalBundle().noteCandidates().get(0).noteType();
        }
        return CanonicalNoteTemplates.GENERIC_NOTE;
    }

    private String inferCreateNoteType(String message) {
        String normalized = normalize(message);
        if (normalized.contains("project") || normalized.contains("roadmap") || normalized.contains("launch")
            || normalized.contains("milestone") || normalized.contains("spec")) {
            return CanonicalNoteTemplates.PROJECT_NOTE;
        }
        return CanonicalNoteTemplates.GENERIC_NOTE;
    }

    private String preferredSection(String message, String noteType) {
        String normalized = normalize(message);
        if (normalized.contains("http://") || normalized.contains("https://") || normalized.contains("www.")) {
            return "references";
        }
        if (normalized.contains("todo") || normalized.contains("task") || normalized.contains("next action")) {
            return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "tasks" : "action_items";
        }
        if (normalized.contains("decid") || normalized.contains("agreed")) {
            return "decisions";
        }
        if (normalized.contains("status") || normalized.contains("progress") || normalized.contains("blocked")) {
            return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "status" : "body";
        }
        if (normalized.contains("question") || normalized.startsWith("why ") || normalized.startsWith("how ")) {
            return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "open_questions" : "body";
        }
        if (containsDate(normalized)) {
            return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "timeline" : "body";
        }
        return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "summary" : "body";
    }

    private String fallbackSection(String preferredSectionId, RouteStrategy strategy, String noteType) {
        if (strategy == RouteStrategy.NOTE_INBOX || strategy == RouteStrategy.NOTEBOOK_INBOX) {
            return "inbox";
        }
        if (preferredSectionId == null || preferredSectionId.isBlank()) {
            return CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType) ? "summary" : "body";
        }
        return preferredSectionId;
    }

    private String formatForSection(String message, String sectionId) {
        String trimmed = message.trim();
        if ("tasks".equals(sectionId) || "action_items".equals(sectionId)) {
            return "- [ ] " + sentenceCase(trimmed);
        }
        if ("decisions".equals(sectionId) || "timeline".equals(sectionId)
            || "open_questions".equals(sectionId) || "references".equals(sectionId)
            || "inbox".equals(sectionId)) {
            return "- " + trimmed;
        }
        return sentenceCase(trimmed);
    }

    private List<NoteSection> rewriteSections(NoteDocumentV1 document, String message) {
        List<NoteSection> rewritten = new ArrayList<>();
        String joined = joinContent(document);
        for (NoteSection section : document.sections()) {
            String content = switch (section.id()) {
                case "summary" -> "Reorganized note based on: " + sentenceCase(message.trim());
                case "status" -> "Updated on " + LocalDate.now(ZoneOffset.UTC) + ": reorganized from existing content.";
                case "body" -> joined;
                case "tasks" -> "- [ ] Review the reorganized note";
                case "inbox" -> "";
                default -> section.contentMarkdown();
            };
            rewritten.add(new NoteSection(section.id(), section.label(), section.kind(), content));
        }
        return rewritten;
    }

    private String summarizeDocument(List<NoteSection> sections) {
        for (NoteSection section : sections) {
            String normalized = summarizeText(section.contentMarkdown());
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String summarizeText(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 140) {
            return normalized;
        }
        return normalized.substring(0, 137) + "...";
    }

    private String suggestTitle(String message) {
        String trimmed = message.trim();
        if (trimmed.length() <= 48) {
            return sentenceCase(trimmed);
        }
        return sentenceCase(trimmed.substring(0, 45)) + "...";
    }

    private String suggestTitle(String message, NoteDocumentV1 document) {
        if (message.toLowerCase(Locale.ROOT).contains("rename")) {
            Matcher matcher = Pattern.compile("rename(?: this note)? to\\s+(.+)$", Pattern.CASE_INSENSITIVE).matcher(message);
            if (matcher.find()) {
                return sentenceCase(matcher.group(1).trim());
            }
        }
        return document.meta().title();
    }

    private boolean looksLikeQuestion(String message) {
        return message.contains("?") || message.startsWith("what ") || message.startsWith("why ")
            || message.startsWith("how ") || message.startsWith("when ") || message.startsWith("where ");
    }

    private boolean looksLikeEditRequest(String message) {
        return message.contains("add ") || message.contains("update ") || message.contains("put this")
            || message.contains("save ") || message.contains("append ") || message.contains("rewrite ");
    }

    private boolean refersToCurrentNote(String message) {
        return message.contains("this note") || message.contains("here") || message.contains("current note")
            || message.contains("rewrite") || message.contains("reorganize") || message.contains("rename");
    }

    private boolean looksLikeRewriteRequest(String message) {
        String normalized = normalize(message);
        return normalized.contains("rewrite this note") || normalized.contains("reorganize this note")
            || normalized.contains("clean up this note") || normalized.contains("rewrite note");
    }

    private boolean looksAmbiguousCapture(String message) {
        return message.contains("remember")
            || message.contains("some point")
            || message.contains("later")
            || message.contains("store this");
    }

    private boolean containsDate(String message) {
        return message.contains("today") || message.contains("yesterday") || message.contains("tomorrow")
            || message.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*");
    }

    private String joinContent(NoteDocumentV1 document) {
        List<String> parts = new ArrayList<>();
        for (NoteSection section : document.sections()) {
            if (!section.contentMarkdown().isBlank()) {
                parts.add(section.label() + ":\n" + section.contentMarkdown());
            }
        }
        return String.join("\n\n", parts);
    }

    private String normalize(String input) {
        return input == null ? "" : input.toLowerCase(Locale.ROOT).trim();
    }

    private String sentenceCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String trimmed = input.trim();
        return Character.toUpperCase(trimmed.charAt(0)) + trimmed.substring(1);
    }

    private AiCallTraceV1 emptyTrace(String model, String promptVersion) {
        return new AiCallTraceV1(model, promptVersion, 0L, null, null, List.of());
    }
}
