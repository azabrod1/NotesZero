package com.notesapp.service.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.domain.ChatEvent;
import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.repository.ChatEventRepository;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.NotebookRepository;
import com.notesapp.config.AiProperties;
import com.notesapp.service.NotFoundException;
import com.notesapp.service.ValidationException;
import com.notesapp.service.aiwrite.AiCallTraceV1;
import com.notesapp.service.aiwrite.AiWriteProvider;
import com.notesapp.service.aiwrite.AiWriteProviderSelector;
import com.notesapp.service.aiwrite.CommitDebugTraceV1;
import com.notesapp.service.aiwrite.DeterministicRetrievalService;
import com.notesapp.service.routing.HybridRetrievalService;
import com.notesapp.service.aiwrite.NoteCandidate;
import com.notesapp.service.aiwrite.NoteRetrievalCandidateTraceV1;
import com.notesapp.service.aiwrite.NotebookRetrievalCandidateTraceV1;
import com.notesapp.service.aiwrite.PatchDecision;
import com.notesapp.service.aiwrite.PatchPlanV1;
import com.notesapp.service.aiwrite.PatchRequestContext;
import com.notesapp.service.aiwrite.RetrievalDebugTraceV1;
import com.notesapp.service.aiwrite.RouteIntent;
import com.notesapp.service.aiwrite.RouteDecision;
import com.notesapp.service.aiwrite.RoutePlanV1;
import com.notesapp.service.aiwrite.RouteRequestContext;
import com.notesapp.service.aiwrite.RouteStrategy;
import com.notesapp.service.aiwrite.RetrievalBundle;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import com.notesapp.web.dto.v2.CommitChatRequest;
import com.notesapp.web.dto.v2.CommitChatResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatCommitService {

    private final ChatEventRepository chatEventRepository;
    private final NotebookRepository notebookRepository;
    private final NoteRepository noteRepository;
    private final DeterministicRetrievalService retrievalService;
    private final HybridRetrievalService hybridRetrievalService;
    private final AiWriteProviderSelector aiWriteProviderSelector;
    private final NoteWorkflowService noteWorkflowService;
    private final CanonicalNoteTemplates canonicalNoteTemplates;
    private final ObjectMapper objectMapper;
    private final AiProperties aiProperties;

    public ChatCommitService(ChatEventRepository chatEventRepository,
                             NotebookRepository notebookRepository,
                             NoteRepository noteRepository,
                             DeterministicRetrievalService retrievalService,
                             HybridRetrievalService hybridRetrievalService,
                             AiWriteProviderSelector aiWriteProviderSelector,
                             NoteWorkflowService noteWorkflowService,
                             CanonicalNoteTemplates canonicalNoteTemplates,
                             ObjectMapper objectMapper,
                             AiProperties aiProperties) {
        this.chatEventRepository = chatEventRepository;
        this.notebookRepository = notebookRepository;
        this.noteRepository = noteRepository;
        this.retrievalService = retrievalService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.aiWriteProviderSelector = aiWriteProviderSelector;
        this.noteWorkflowService = noteWorkflowService;
        this.canonicalNoteTemplates = canonicalNoteTemplates;
        this.objectMapper = objectMapper;
        this.aiProperties = aiProperties;
    }

    @Transactional
    public CommitChatResponse commit(CommitChatRequest request) {
        ChatEvent chatEvent = new ChatEvent();
        chatEvent.setUserId(1L);
        chatEvent.setMessage(request.getMessage().trim());
        chatEvent.setSelectedNotebook(resolveNotebook(request.getSelectedNotebookId()));
        chatEvent.setSelectedNote(resolveNote(request.getSelectedNoteId()));
        chatEvent.setCreatedAt(Instant.now());
        chatEvent = chatEventRepository.save(chatEvent);

        long retrievalStartedAt = System.nanoTime();
        boolean useHybrid = "hybrid".equalsIgnoreCase(aiProperties.getRetrievalMode());
        RetrievalBundle retrievalBundle = useHybrid
            ? hybridRetrievalService.retrieve(request.getMessage(), request.getSelectedNotebookId(), request.getSelectedNoteId())
            : retrievalService.retrieve(request.getMessage(), request.getSelectedNotebookId(), request.getSelectedNoteId());
        NoteDocumentV1 selectedNoteDocument = request.getSelectedNoteId() == null
            ? null
            : (useHybrid
                ? hybridRetrievalService.getSelectedNoteDocument(request.getSelectedNoteId())
                : retrievalService.getSelectedNoteDocument(request.getSelectedNoteId()));
        long retrievalLatencyMs = elapsedMillis(retrievalStartedAt);

        AiWriteProvider provider = aiWriteProviderSelector.activeProvider();
        RouteRequestContext routeRequestContext = new RouteRequestContext(
            request.getMessage(),
            request.getSelectedNotebookId(),
            request.getSelectedNoteId(),
            recentMessages(request.getRecentChatEventIds()),
            retrievalBundle,
            selectedNoteDocument
        );
        RouteDecision routeDecision = provider.routeWithTrace(routeRequestContext);
        RoutePlanV1 routePlan = routeDecision.routePlan();
        AiCallTraceV1 routeTrace = routeDecision.trace();

        // Handle NEED_MORE_CONTEXT: fetch requested notes and retry routing once
        if (routePlan.intent() == RouteIntent.NEED_MORE_CONTEXT
            && routePlan.needContextNoteIds() != null
            && !routePlan.needContextNoteIds().isEmpty()) {
            NoteDocumentV1 extraDocument = noteWorkflowService.loadDocument(routePlan.needContextNoteIds().get(0));
            RouteRequestContext retryContext = new RouteRequestContext(
                request.getMessage(),
                request.getSelectedNotebookId(),
                request.getSelectedNoteId(),
                recentMessages(request.getRecentChatEventIds()),
                retrievalBundle,
                extraDocument != null ? extraDocument : selectedNoteDocument
            );
            RouteDecision retryDecision = provider.routeWithTrace(retryContext);
            routePlan = retryDecision.routePlan();
            routeTrace = retryDecision.trace();
            // If still NEED_MORE_CONTEXT after retry, fall back to CLARIFY
            if (routePlan.intent() == RouteIntent.NEED_MORE_CONTEXT) {
                routePlan = new RoutePlanV1(
                    RouteIntent.CLARIFY,
                    routePlan.targetNotebookId(),
                    routePlan.targetNoteId(),
                    routePlan.targetNoteType(),
                    routePlan.confidence(),
                    routePlan.reasonCodes(),
                    RouteStrategy.CLARIFY,
                    "I'm not sure which note to update. Could you clarify?"
                );
            }
        }

        chatEvent.setRoutePlanJson(writeJson(routePlan));
        RetrievalDebugTraceV1 retrievalTrace = request.isIncludeDebugTrace()
            ? buildRetrievalTrace(request, retrievalBundle, retrievalLatencyMs)
            : null;

        if (routePlan.intent() == RouteIntent.ANSWER_ONLY
            || routePlan.intent() == RouteIntent.CLARIFY) {
            String answer = routePlan.intent() == RouteIntent.ANSWER_ONLY
                ? answer(request.getMessage(), routePlan, retrievalBundle)
                : routePlan.answer();
            if (answer == null || answer.isBlank()) {
                answer = routePlan.intent() == RouteIntent.ANSWER_ONLY
                    ? answer(request.getMessage(), routePlan, retrievalBundle)
                    : "Which notebook or note should I update?";
            }
            chatEvent.setApplyResultJson(writeJson(java.util.Map.of("answer", answer)));
            chatEventRepository.save(chatEvent);
            return new CommitChatResponse(
                chatEvent.getId(),
                routePlan,
                new PatchPlanV1(routePlan.targetNotebookId(), routePlan.targetNoteId(), routePlan.targetNoteType(), List.of(), false, provider.plannerPromptVersion()),
                null,
                routePlan.targetNoteId() == null ? null : noteWorkflowService.getNote(routePlan.targetNoteId()),
                List.of(),
                null,
                null,
                answer,
                request.isIncludeDebugTrace() ? new CommitDebugTraceV1(retrievalTrace, routeTrace, null, null) : null
            );
        }

        NoteDocumentV1 targetDocument = targetDocument(routePlan);
        PatchDecision patchDecision = provider.planWithTrace(new PatchRequestContext(request.getMessage(), routePlan, targetDocument));
        PatchPlanV1 patchPlan = patchDecision.patchPlan();
        AiCallTraceV1 patchTrace = patchDecision.trace();
        chatEvent.setPatchPlanJson(writeJson(patchPlan));

        NoteWorkflowService.MutationResult mutationResult;
        Long expectedRevisionId = expectedRevisionIdForRoute(request, routePlan);
        long mutationStartedAt = System.nanoTime();
        try {
            mutationResult = noteWorkflowService.applyPlannedMutation(
                routePlan,
                patchPlan,
                chatEvent,
                expectedRevisionId,
                provider
            );
        } catch (ValidationException ex) {
            if (!shouldRetryInInbox(ex)) {
                throw ex;
            }
            RoutePlanV1 fallbackRoute = fallbackRoute(routePlan);
            PatchDecision fallbackPatchDecision = provider.planWithTrace(new PatchRequestContext(request.getMessage(), fallbackRoute, targetDocument(fallbackRoute)));
            PatchPlanV1 fallbackPlan = fallbackPatchDecision.patchPlan();
            mutationResult = noteWorkflowService.applyPlannedMutation(
                fallbackRoute,
                fallbackPlan,
                chatEvent,
                expectedRevisionIdForRoute(request, fallbackRoute),
                provider
            );
            patchPlan = fallbackPlan;
            routePlan = fallbackRoute;
            routeTrace = routeTrace == null ? null : routeTrace.withAdditionalOverride("validator_retry_to_inbox");
            patchTrace = fallbackPatchDecision.trace() == null
                ? null
                : fallbackPatchDecision.trace().withAdditionalOverride("validator_retry_to_inbox");
        }
        long mutationLatencyMs = elapsedMillis(mutationStartedAt);

        chatEvent.setRoutePlanJson(writeJson(routePlan));
        chatEvent.setPatchPlanJson(writeJson(patchPlan));
        chatEvent.setApplyResultJson(writeJson(mutationResult.applyResult()));
        chatEventRepository.save(chatEvent);

        return new CommitChatResponse(
            chatEvent.getId(),
            routePlan,
            patchPlan,
            mutationResult.applyResult(),
            mutationResult.updatedNote(),
            mutationResult.diff(),
            mutationResult.provenance(),
            mutationResult.undoToken(),
            null,
            request.isIncludeDebugTrace() ? new CommitDebugTraceV1(retrievalTrace, routeTrace, patchTrace, mutationLatencyMs) : null
        );
    }

    private List<String> recentMessages(List<Long> recentChatEventIds) {
        if (recentChatEventIds == null || recentChatEventIds.isEmpty()) {
            return List.of();
        }
        return chatEventRepository.findAllById(recentChatEventIds).stream()
            .sorted(Comparator.comparing(ChatEvent::getCreatedAt))
            .map(ChatEvent::getMessage)
            .filter(message -> message != null && !message.isBlank())
            .limit(4)
            .toList();
    }

    Long expectedRevisionIdForRoute(CommitChatRequest request, RoutePlanV1 routePlan) {
        if (request.getCurrentRevisionId() == null || request.getSelectedNoteId() == null) {
            return null;
        }
        if (routePlan.intent() != RouteIntent.WRITE_EXISTING_NOTE) {
            return null;
        }
        if (routePlan.targetNoteId() == null) {
            return null;
        }
        return request.getSelectedNoteId().equals(routePlan.targetNoteId())
            ? request.getCurrentRevisionId()
            : null;
    }

    private boolean shouldRetryInInbox(ValidationException ex) {
        String message = ex.getMessage();
        if (message == null) {
            return true;
        }
        return !message.toLowerCase(Locale.ROOT).contains("revision mismatch");
    }

    private RoutePlanV1 fallbackRoute(RoutePlanV1 routePlan) {
        List<String> reasons = new ArrayList<>(routePlan.reasonCodes());
        reasons.add("validator_retry_to_inbox");
        RouteStrategy strategy = routePlan.targetNoteId() != null ? RouteStrategy.NOTE_INBOX : RouteStrategy.NOTEBOOK_INBOX;
        return new RoutePlanV1(
            routePlan.intent(),
            routePlan.targetNotebookId(),
            routePlan.targetNoteId(),
            routePlan.targetNoteType(),
            routePlan.confidence(),
            reasons,
            strategy,
            routePlan.answer()
        );
    }

    private NoteDocumentV1 targetDocument(RoutePlanV1 routePlan) {
        if (routePlan.targetNoteId() != null) {
            return noteWorkflowService.loadDocument(routePlan.targetNoteId());
        }
        return canonicalNoteTemplates.createTemplate(
            routePlan.targetNoteType(),
            routePlan.targetNotebookId(),
            routePlan.strategy() == RouteStrategy.NOTEBOOK_INBOX ? "Inbox" : "Untitled note"
        );
    }

    private String answer(String message, RoutePlanV1 routePlan, RetrievalBundle retrievalBundle) {
        if (routePlan.targetNoteId() != null) {
            NoteDocumentV1 document = noteWorkflowService.loadDocument(routePlan.targetNoteId());
            String answerFromDocument = answerFromDocument(message, document);
            if (answerFromDocument != null && !answerFromDocument.isBlank()) {
                return answerFromDocument;
            }
        }
        if (routePlan.answer() != null && !routePlan.answer().isBlank()) {
            return routePlan.answer();
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (retrievalBundle.noteCandidates().isEmpty()) {
            return "I could not find a relevant note yet.";
        }
        NoteCandidate top = retrievalBundle.noteCandidates().get(0);
        if (normalized.contains("where")) {
            return "Best match: " + top.title();
        }
        if (normalized.contains("what") || normalized.contains("summary")) {
            return top.summaryShort().isBlank() ? "The closest note is " + top.title() + "." : top.summaryShort();
        }
        return "Closest note: " + top.title() + ". " + top.summaryShort();
    }

    private String answerFromDocument(String message, NoteDocumentV1 document) {
        if (document == null || document.sections() == null || document.sections().isEmpty()) {
            return null;
        }

        List<NoteSection> visibleSections = document.sections().stream()
            .filter(section -> !NoteSectionVisibility.isHidden(section))
            .filter(section -> section.contentMarkdown() != null && !section.contentMarkdown().isBlank())
            .toList();
        if (visibleSections.isEmpty()) {
            return fallbackDocumentSummary(document);
        }

        NoteSection requestedSection = requestedSection(message, visibleSections);
        if (requestedSection == null) {
            requestedSection = bestMatchingSection(message, visibleSections);
        }
        if (requestedSection == null) {
            requestedSection = visibleSections.get(0);
        }

        String excerpt = excerpt(requestedSection.contentMarkdown());
        if (excerpt == null || excerpt.isBlank()) {
            return fallbackDocumentSummary(document);
        }

        String title = document.meta() == null || document.meta().title() == null
            ? "this note"
            : document.meta().title();
        return "In " + title + ", " + requestedSection.label() + ": " + excerpt;
    }

    private String fallbackDocumentSummary(NoteDocumentV1 document) {
        if (document == null || document.meta() == null) {
            return null;
        }
        String title = document.meta().title() == null ? "this note" : document.meta().title();
        String summary = document.meta().summaryShort();
        if (summary != null && !summary.isBlank()) {
            return "In " + title + ": " + summary;
        }
        return "I found " + title + ", but it does not have enough content yet to answer that.";
    }

    private NoteSection requestedSection(String message, List<NoteSection> sections) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (normalized.contains("status")) {
            return sectionByIdOrLabel(sections, Set.of("status"));
        }
        if (normalized.contains("summary")) {
            return sectionByIdOrLabel(sections, Set.of("summary"));
        }
        if (normalized.contains("decision")) {
            return sectionByIdOrLabel(sections, Set.of("decisions", "decision"));
        }
        if (normalized.contains("task") || normalized.contains("todo") || normalized.contains("action item")) {
            return sectionByIdOrLabel(sections, Set.of("tasks", "action_items", "task list", "action items"));
        }
        if (normalized.contains("timeline") || normalized.contains("when")) {
            return sectionByIdOrLabel(sections, Set.of("timeline"));
        }
        if (normalized.contains("reference") || normalized.contains("link") || normalized.contains("docs")) {
            return sectionByIdOrLabel(sections, Set.of("references", "reference"));
        }
        if (normalized.contains("question")) {
            return sectionByIdOrLabel(sections, Set.of("open_questions", "open questions"));
        }
        if (normalized.contains("body") || normalized.contains("observation")) {
            return sectionByIdOrLabel(sections, Set.of("body"));
        }
        return null;
    }

    private NoteSection sectionByIdOrLabel(List<NoteSection> sections, Set<String> candidates) {
        for (NoteSection section : sections) {
            String id = section.id() == null ? "" : section.id().toLowerCase(Locale.ROOT);
            String label = section.label() == null ? "" : section.label().toLowerCase(Locale.ROOT);
            for (String candidate : candidates) {
                if (id.equals(candidate) || label.equals(candidate)) {
                    return section;
                }
            }
        }
        return null;
    }

    private NoteSection bestMatchingSection(String message, List<NoteSection> sections) {
        Set<String> tokens = tokenize(message);
        Set<String> topicTokens = tokenize(extractTopicPhrase(message));
        NoteSection bestSection = null;
        int bestScore = Integer.MIN_VALUE;
        for (NoteSection section : sections) {
            Set<String> sectionTokens = new HashSet<>(tokenize(section.label()));
            sectionTokens.addAll(tokenize(section.contentMarkdown()));
            int score = overlap(tokens, sectionTokens);
            if (!topicTokens.isEmpty()) {
                score += overlap(topicTokens, sectionTokens) * 3;
            }
            if ("summary".equalsIgnoreCase(section.id()) && !topicTokens.isEmpty()) {
                score -= 1;
            }
            if (score > bestScore) {
                bestScore = score;
                bestSection = section;
            }
        }
        return bestSection;
    }

    private String extractTopicPhrase(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String normalized = message.trim();
        int aboutIndex = normalized.toLowerCase(Locale.ROOT).lastIndexOf(" about ");
        if (aboutIndex < 0) {
            return "";
        }
        String topic = normalized.substring(aboutIndex + 7).trim();
        int questionMark = topic.indexOf('?');
        if (questionMark >= 0) {
            topic = topic.substring(0, questionMark);
        }
        return topic.trim();
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private String excerpt(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return null;
        }
        String normalized = markdown
            .replace("\r", "\n")
            .replaceAll("(?m)^#+\\s*", "")
            .replaceAll("(?m)^- \\[ \\]\\s*", "")
            .replaceAll("(?m)^- \\[x\\]\\s*", "")
            .replaceAll("(?m)^[-*]\\s*", "")
            .replaceAll("`", "")
            .replaceAll("\\[(.+?)]\\((.+?)\\)", "$1 ($2)")
            .replaceAll("\\s+", " ")
            .trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() <= 220) {
            return normalized;
        }
        return normalized.substring(0, 217).trim() + "...";
    }

    private Notebook resolveNotebook(Long notebookId) {
        if (notebookId == null) {
            return null;
        }
        return notebookRepository.findById(notebookId)
            .orElseThrow(() -> new NotFoundException("Notebook not found: " + notebookId));
    }

    private Note resolveNote(Long noteId) {
        if (noteId == null) {
            return null;
        }
        return noteRepository.findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize chat event payload", e);
        }
    }

    private RetrievalDebugTraceV1 buildRetrievalTrace(CommitChatRequest request,
                                                      RetrievalBundle retrievalBundle,
                                                      long latencyMs) {
        return new RetrievalDebugTraceV1(
            request.getSelectedNotebookId(),
            request.getSelectedNoteId(),
            latencyMs,
            topGap(retrievalBundle.notebookCandidates().stream().map(candidate -> candidate.score()).toList()),
            topGap(retrievalBundle.noteCandidates().stream().map(candidate -> candidate.score()).toList()),
            retrievalBundle.notebookCandidates().stream()
                .map(candidate -> new NotebookRetrievalCandidateTraceV1(
                    candidate.notebookId(),
                    candidate.name(),
                    candidate.routingSummary(),
                    candidate.score()
                ))
                .toList(),
            retrievalBundle.noteCandidates().stream()
                .map(candidate -> new NoteRetrievalCandidateTraceV1(
                    candidate.noteId(),
                    candidate.notebookId(),
                    candidate.title(),
                    candidate.summaryShort(),
                    candidate.noteType(),
                    candidate.sectionLabels(),
                    candidate.topSectionSnippet(),
                    candidate.updatedAt(),
                    candidate.score(),
                    candidate.exactTitleMatch()
                ))
                .toList()
        );
    }

    private Double topGap(List<Double> scores) {
        if (scores == null || scores.size() < 2) {
            return null;
        }
        return scores.get(0) - scores.get(1);
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }
}
