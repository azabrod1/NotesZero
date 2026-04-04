package com.notesapp.service.aiwrite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.notesapp.config.AiProperties;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class OpenAiResponsesAiWriteProvider implements AiWriteProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesAiWriteProvider.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private static final String NANO_TRIAGE_INSTRUCTIONS = """
        You are a fast message classifier for NotesZero, an AI-native note-taking app.

        Classify the user's message into one of four types:
        - "write": the user wants to create or update a note (add info, capture something, save, update, edit)
        - "question": the user is asking for information from their notes (what does X say, where is Y, tell me about Z)
        - "cancel": the user is cancelling or saying nothing needs to happen ("nothing", "never mind", "cancel", "skip", "ignore", "forget it", "no thanks", "don't worry", "actually nevermind")
        - "chitchat": a greeting, meta question about the app, or irrelevant social message ("hello", "what does this app do", "how does this work", "thanks", "ok", "cool")

        Rules:
        - For "write" and "question": set reply to ""
        - For "cancel": write a brief natural acknowledgment in reply (e.g. "Got it, nothing changed.", "No problem.", "Understood, I'll leave things as they are.")
        - For "chitchat": write one helpful sentence in reply (e.g. for greetings: "Hi! What would you like to capture today?"; for app questions: "NotesZero is an AI-native notes app — just tell me what to note down.")

        Be decisive. If unsure between write and question, pick write.
        """;

    private record NanoTriageRaw(String type, String reply) {}

    private final AiProperties aiProperties;
    private final CanonicalNoteTemplates canonicalNoteTemplates;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenAiResponsesAiWriteProvider(AiProperties aiProperties,
                                          CanonicalNoteTemplates canonicalNoteTemplates,
                                          ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.canonicalNoteTemplates = canonicalNoteTemplates;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    public boolean isConfigured() {
        return aiProperties.getOpenAiApiKey() != null && !aiProperties.getOpenAiApiKey().isBlank();
    }

    @Override
    public NanoTriageResult triage(String message) {
        StructuredInvocationResult<NanoTriageRaw> raw = invokeStructured(
            aiProperties.getTriageModel(),
            null,
            NANO_TRIAGE_INSTRUCTIONS,
            "User message: " + message,
            "noteszero_nano_triage_v1",
            triageSchema(),
            new TypeReference<>() {
            }
        );
        NanoTriageResult.TriageType triageType = switch (raw.value().type()) {
            case "cancel" -> NanoTriageResult.TriageType.CANCEL;
            case "chitchat" -> NanoTriageResult.TriageType.CHITCHAT;
            case "question" -> NanoTriageResult.TriageType.QUESTION;
            default -> NanoTriageResult.TriageType.WRITE;
        };
        return new NanoTriageResult(triageType, raw.value().reply());
    }

    @Override
    public RouteDecision routeWithTrace(RouteRequestContext context) {
        StructuredInvocationResult<RoutePlanV1> raw = invokeStructured(
            aiProperties.getRouterModel(),
            "low",
            OpenAiWritePrompts.routeInstructions(),
            OpenAiWritePrompts.routeInput(context),
            "noteszero_route_plan_v1",
            routeSchema(),
            new TypeReference<>() {
            }
        );
        RoutePlanV1 sanitized = sanitizeRoutePlan(context, raw.value());
        return new RouteDecision(
            sanitized,
            new AiCallTraceV1(
                routerModel(),
                routerPromptVersion(),
                raw.latencyMs(),
                raw.responseId(),
                raw.usage(),
                diffRouteOverrides(raw.value(), sanitized)
            )
        );
    }

    @Override
    public PatchDecision planWithTrace(PatchRequestContext context) {
        try {
            StructuredInvocationResult<PatchPlanV1> raw = invokeStructured(
                aiProperties.getPlannerModel(),
                "low",
                OpenAiWritePrompts.plannerInstructions(),
                OpenAiWritePrompts.plannerInput(context),
                "noteszero_patch_plan_v1",
                patchSchema(),
                new TypeReference<>() {
                }
            );
            PatchPlanV1 sanitized = sanitizePatchPlan(context, raw.value());
            return new PatchDecision(
                sanitized,
                new AiCallTraceV1(
                    plannerModel(),
                    plannerPromptVersion(),
                    raw.latencyMs(),
                    raw.responseId(),
                    raw.usage(),
                    diffPatchOverrides(raw.value(), sanitized)
                )
            );
        } catch (IllegalStateException ex) {
            log.warn("Planner response was unusable; falling back to deterministic patch plan. message={}", ex.getMessage());
            PatchPlanV1 fallback = fallbackPatchPlan(context);
            return new PatchDecision(
                fallback,
                new AiCallTraceV1(
                    plannerModel(),
                    plannerPromptVersion(),
                    0L,
                    null,
                    null,
                    List.of("planner_fallback")
                )
            );
        }
    }

    @Override
    public NoteSummaryResult summarize(NoteDocumentV1 document) {
        StructuredInvocationResult<NoteSummaryResult> raw = invokeStructured(
            aiProperties.getSummaryModel(),
            "low",
            OpenAiWritePrompts.summaryInstructions(),
            OpenAiWritePrompts.summaryInput(document),
            "noteszero_note_summary_v1",
            summarySchema(),
            new TypeReference<>() {
            }
        );
        return new NoteSummaryResult(
            trimToLength(raw.value().summaryShort(), 160),
            trimToLength(raw.value().routingSummary(), 220)
        );
    }

    @Override
    public String providerName() {
        return "openai";
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
        return aiProperties.getRouterPromptId().isBlank()
            ? OpenAiWritePrompts.ROUTER_PROMPT_VERSION
            : aiProperties.getRouterPromptId();
    }

    @Override
    public String plannerPromptVersion() {
        return aiProperties.getPlannerPromptId().isBlank()
            ? OpenAiWritePrompts.PLANNER_PROMPT_VERSION
            : aiProperties.getPlannerPromptId();
    }

    private <T> StructuredInvocationResult<T> invokeStructured(String model,
                                                               String reasoningEffort,
                                                               String instructions,
                                                               String inputText,
                                                               String schemaName,
                                                               ObjectNode schema,
                                                               TypeReference<T> typeReference) {
        if (!isConfigured()) {
            throw new IllegalStateException("OpenAI provider selected but OPENAI_API_KEY is not configured.");
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("store", false);
        requestBody.put("instructions", instructions);
        requestBody.put("max_output_tokens", maxOutputTokens(schemaName));

        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            ObjectNode reasoning = requestBody.putObject("reasoning");
            reasoning.put("effort", reasoningEffort);
        }

        ArrayNode input = requestBody.putArray("input");
        ObjectNode message = input.addObject();
        message.put("role", "user");
        ArrayNode content = message.putArray("content");
        content.addObject()
            .put("type", "input_text")
            .put("text", inputText);

        ObjectNode text = requestBody.putObject("text");
        ObjectNode format = text.putObject("format");
        format.put("type", "json_schema");
        format.put("name", schemaName);
        format.put("strict", true);
        format.set("schema", schema);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(normalizeBaseUrl() + "/responses"))
            .header("Authorization", "Bearer " + aiProperties.getOpenAiApiKey().trim())
            .header("Content-Type", "application/json")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(writeJson(requestBody)))
            .build();

        long startedAt = System.nanoTime();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new IllegalStateException("OpenAI Responses API failed with status "
                    + response.statusCode() + ": " + safeErrorMessage(response.body()));
            }

            JsonNode body = objectMapper.readTree(response.body());
            validateCompletedResponse(body);
            String outputText = extractOutputText(body);
            if (outputText == null || outputText.isBlank()) {
                throw new IllegalStateException("OpenAI response did not contain structured output text.");
            }

            log.info("OpenAI structured response completed: model={}, responseId={}", model, body.path("id").asText("unknown"));
            return new StructuredInvocationResult<>(
                readStructuredValue(outputText, typeReference),
                parseUsage(body.path("usage")),
                body.path("id").asText(null),
                (System.nanoTime() - startedAt) / 1_000_000L
            );
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse OpenAI response.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenAI request was interrupted.", e);
        }
    }

    private RoutePlanV1 sanitizeRoutePlan(RouteRequestContext context, RoutePlanV1 raw) {
        RouteIntent intent = raw.intent() == null ? RouteIntent.CLARIFY : raw.intent();
        Set<Long> allowedNotebookIds = allowedNotebookIds(context);
        Map<Long, NoteCandidate> allowedNotes = allowedNotes(context);
        NoteCandidate explicitMentionedNote = findExplicitMentionedNote(context.message(), allowedNotes.values());
        NoteCandidate topRetrievedNote = topNoteCandidate(context);
        Double topNoteGap = topNoteGap(context.retrievalBundle());
        boolean explicitCreateRequest = isExplicitCreateRequest(context.message());
        boolean lowSpecificityCapture = isLowSpecificityCapture(context.message());
        boolean questionLikeMessage = looksLikeQuestion(context.message());
        boolean selectedNoteUpdate = shouldStickToSelectedNote(context, explicitMentionedNote, explicitCreateRequest);
        double confidence = adjustConfidenceMultiSignal(context, clamp(raw.confidence()),
            raw.targetNoteId(), raw.targetNotebookId(), explicitMentionedNote, selectedNoteUpdate);

        Long targetNoteId = allowedNotes.containsKey(raw.targetNoteId()) ? raw.targetNoteId() : null;
        Long targetNotebookId = allowedNotebookIds.contains(raw.targetNotebookId()) ? raw.targetNotebookId() : null;

        if (targetNoteId != null && targetNotebookId == null) {
            targetNotebookId = allowedNotes.get(targetNoteId).notebookId();
        }
        if (targetNotebookId == null && context.selectedNotebookId() != null && allowedNotebookIds.contains(context.selectedNotebookId())) {
            targetNotebookId = context.selectedNotebookId();
        }
        if (targetNoteId == null
            && intent != RouteIntent.CREATE_NOTE
            && context.selectedNoteId() != null
            && allowedNotes.containsKey(context.selectedNoteId())) {
            targetNoteId = context.selectedNoteId();
            targetNotebookId = allowedNotes.get(targetNoteId).notebookId();
        }

        String targetNoteType = canonicalNoteTemplates.normalizeNoteType(raw.targetNoteType());
        if (targetNoteId != null) {
            targetNoteType = allowedNotes.get(targetNoteId).noteType();
        }
        if (targetNoteId == null && targetNotebookId == null && !context.retrievalBundle().notebookCandidates().isEmpty()) {
            targetNotebookId = context.retrievalBundle().notebookCandidates().get(0).notebookId();
        }

        RouteStrategy strategy = raw.strategy();
        List<String> reasonCodes = sanitizeReasonCodes(raw.reasonCodes());
        String answer = trimToLength(raw.answer(), 500);
        String requestedNoteType = requestedCreateNoteType(context.message());
        boolean forcedNotebookInbox = false;

        if (requestedNoteType != null) {
            targetNoteType = requestedNoteType;
        }

        RoutePlanV1 notebookCaptureRedirect = maybeRedirectCaptureToTopNotebook(
            context,
            raw,
            intent,
            targetNotebookId,
            targetNoteId,
            targetNoteType,
            confidence,
            reasonCodes,
            strategy,
            explicitMentionedNote,
            explicitCreateRequest,
            lowSpecificityCapture,
            selectedNoteUpdate,
            questionLikeMessage
        );
        if (notebookCaptureRedirect != null) {
            return notebookCaptureRedirect;
        }

        RoutePlanV1 selectedNoteCaptureRedirect = maybeRedirectSelectedNoteCapture(
            context,
            raw,
            intent,
            targetNotebookId,
            targetNoteId,
            targetNoteType,
            confidence,
            reasonCodes,
            explicitMentionedNote,
            explicitCreateRequest,
            selectedNoteUpdate,
            questionLikeMessage
        );
        if (selectedNoteCaptureRedirect != null) {
            return selectedNoteCaptureRedirect;
        }

        RoutePlanV1 concreteCapturePromotion = maybePromoteConcreteNotebookCapture(
            context,
            intent,
            targetNotebookId,
            targetNoteId,
            targetNoteType,
            confidence,
            reasonCodes,
            strategy,
            explicitMentionedNote,
            explicitCreateRequest,
            lowSpecificityCapture,
            questionLikeMessage
        );
        if (concreteCapturePromotion != null) {
            return concreteCapturePromotion;
        }

        if (explicitMentionedNote != null && !explicitCreateRequest) {
            targetNoteId = explicitMentionedNote.noteId();
            targetNotebookId = explicitMentionedNote.notebookId();
            targetNoteType = explicitMentionedNote.noteType();
            if (questionLikeMessage) {
                intent = RouteIntent.ANSWER_ONLY;
                strategy = RouteStrategy.ANSWER_ONLY;
                answer = null;
            } else {
                intent = RouteIntent.WRITE_EXISTING_NOTE;
                strategy = RouteStrategy.DIRECT_APPLY;
            }
        } else if (selectedNoteUpdate) {
            NoteCandidate selectedNote = allowedNotes.get(context.selectedNoteId());
            if (selectedNote != null) {
                targetNoteId = selectedNote.noteId();
                targetNotebookId = selectedNote.notebookId();
                targetNoteType = selectedNote.noteType();
                intent = RouteIntent.WRITE_EXISTING_NOTE;
                strategy = RouteStrategy.DIRECT_APPLY;
            }
        }

        if (intent == RouteIntent.ANSWER_ONLY) {
            strategy = RouteStrategy.ANSWER_ONLY;
            if (targetNoteId == null && (answer == null || answer.isBlank())) {
                answer = "I need more context from your notes to answer that safely.";
            }
            return new RoutePlanV1(intent, targetNotebookId, targetNoteId, targetNoteType, confidence, reasonCodes, strategy, answer);
        }

        if (intent == RouteIntent.CLARIFY) {
            strategy = RouteStrategy.CLARIFY;
            if (answer == null || answer.isBlank()) {
                answer = "Which notebook or note should I update?";
            }
            return new RoutePlanV1(intent, targetNotebookId, targetNoteId, targetNoteType, confidence, reasonCodes, strategy, answer);
        }

        if (intent == RouteIntent.NEED_MORE_CONTEXT) {
            return new RoutePlanV1(intent, targetNotebookId, targetNoteId, targetNoteType, confidence,
                reasonCodes, RouteStrategy.CLARIFY, answer, raw.needContextNoteIds());
        }

        if (explicitCreateRequest) {
            intent = RouteIntent.CREATE_NOTE;
            targetNoteId = null;
            if (targetNotebookId == null
                && context.selectedNotebookId() != null
                && allowedNotebookIds.contains(context.selectedNotebookId())) {
                targetNotebookId = context.selectedNotebookId();
            }
        }

        if (!explicitCreateRequest && lowSpecificityCapture && explicitMentionedNote == null) {
            intent = RouteIntent.CREATE_NOTE;
            targetNoteId = null;
            strategy = RouteStrategy.NOTEBOOK_INBOX;
            forcedNotebookInbox = true;
            targetNoteType = requestedNoteType != null ? requestedNoteType : CanonicalNoteTemplates.GENERIC_NOTE;
            if (targetNotebookId == null
                && context.selectedNotebookId() != null
                && allowedNotebookIds.contains(context.selectedNotebookId())) {
                targetNotebookId = context.selectedNotebookId();
            }
        }

        if (intent == RouteIntent.WRITE_EXISTING_NOTE && targetNoteId == null) {
            if (topRetrievedNote != null
                && topRetrievedNote.score() >= aiProperties.getRouteAutoApplyThreshold()
                && !isAmbiguousNoteGap(topRetrievedNote, topNoteGap)) {
                NoteCandidate top = topRetrievedNote;
                targetNoteId = top.noteId();
                targetNotebookId = top.notebookId();
                targetNoteType = top.noteType();
            } else {
                intent = targetNotebookId != null ? RouteIntent.CREATE_NOTE : RouteIntent.CLARIFY;
            }
        }

        if (shouldPreferNotebookInbox(context, intent, strategy, targetNoteId, reasonCodes, allowedNotes, explicitMentionedNote != null)) {
            intent = RouteIntent.CREATE_NOTE;
            targetNoteId = null;
            strategy = RouteStrategy.NOTEBOOK_INBOX;
            forcedNotebookInbox = true;
            targetNoteType = requestedNoteType != null ? requestedNoteType : CanonicalNoteTemplates.GENERIC_NOTE;
            if (targetNotebookId == null
                && context.selectedNotebookId() != null
                && allowedNotebookIds.contains(context.selectedNotebookId())) {
                targetNotebookId = context.selectedNotebookId();
            }
        }

        if (intent == RouteIntent.CREATE_NOTE && targetNotebookId == null) {
            intent = RouteIntent.CLARIFY;
            strategy = RouteStrategy.CLARIFY;
            answer = answer == null || answer.isBlank() ? "Which notebook should I put that in?" : answer;
            return new RoutePlanV1(intent, null, null, targetNoteType, confidence, reasonCodes, strategy, answer);
        }

        if (intent == RouteIntent.WRITE_EXISTING_NOTE) {
            if (targetNoteId == null) {
                intent = RouteIntent.CLARIFY;
                strategy = RouteStrategy.CLARIFY;
                answer = "Which note should I update?";
                return new RoutePlanV1(intent, targetNotebookId, null, targetNoteType, confidence, reasonCodes, strategy, answer);
            }
            NoteCandidate targetNote = allowedNotes.get(targetNoteId);
            if (strategy == RouteStrategy.DIRECT_APPLY
                && targetNote != null
                && isAmbiguousNoteGap(topRetrievedNote, topNoteGap)
                && !hasStrongTargetEvidence(context, targetNote, explicitMentionedNote, selectedNoteUpdate)) {
                strategy = RouteStrategy.NOTE_INBOX;
            }
            if (strategy != RouteStrategy.DIRECT_APPLY && strategy != RouteStrategy.NOTE_INBOX) {
                strategy = confidence >= aiProperties.getRouteAutoApplyThreshold()
                    ? RouteStrategy.DIRECT_APPLY
                    : RouteStrategy.NOTE_INBOX;
            }
            if (confidence < aiProperties.getRouteAutoApplyThreshold() && strategy == RouteStrategy.DIRECT_APPLY) {
                strategy = RouteStrategy.NOTE_INBOX;
            }
        } else if (intent == RouteIntent.CREATE_NOTE) {
            if (requestedNoteType != null) {
                targetNoteType = requestedNoteType;
            }
            if (strategy != RouteStrategy.DIRECT_APPLY && strategy != RouteStrategy.NOTEBOOK_INBOX) {
                strategy = confidence >= aiProperties.getRouteAutoApplyThreshold()
                    ? RouteStrategy.DIRECT_APPLY
                    : RouteStrategy.NOTEBOOK_INBOX;
            }
            if (!forcedNotebookInbox
                && strategy == RouteStrategy.NOTEBOOK_INBOX
                && context.selectedNotebookId() != null
                && context.selectedNotebookId().equals(targetNotebookId)
                && confidence >= aiProperties.getRouteAutoApplyThreshold()) {
                strategy = RouteStrategy.DIRECT_APPLY;
            }
            if (confidence < aiProperties.getRouteAutoApplyThreshold() && strategy == RouteStrategy.DIRECT_APPLY) {
                strategy = RouteStrategy.NOTEBOOK_INBOX;
            }
            targetNoteId = null;
        }

        return new RoutePlanV1(intent, targetNotebookId, targetNoteId, targetNoteType, confidence, reasonCodes, strategy, null);
    }

    private RoutePlanV1 maybeRedirectCaptureToTopNotebook(RouteRequestContext context,
                                                          RoutePlanV1 raw,
                                                          RouteIntent intent,
                                                          Long targetNotebookId,
                                                          Long targetNoteId,
                                                          String targetNoteType,
                                                          double confidence,
                                                          List<String> reasonCodes,
                                                          RouteStrategy strategy,
                                                          NoteCandidate explicitMentionedNote,
                                                          boolean explicitCreateRequest,
                                                          boolean lowSpecificityCapture,
                                                          boolean selectedNoteUpdate,
                                                          boolean questionLikeMessage) {
        if (explicitMentionedNote != null
            || selectedNoteUpdate
            || questionLikeMessage
            || intent == RouteIntent.ANSWER_ONLY
            || intent == RouteIntent.CLARIFY
            || context.retrievalBundle().notebookCandidates().isEmpty()) {
            return null;
        }

        NotebookCandidate topNotebook = context.retrievalBundle().notebookCandidates().getFirst();
        NotebookCandidate chosenNotebook = null;
        for (NotebookCandidate candidate : context.retrievalBundle().notebookCandidates()) {
            if (targetNotebookId != null && targetNotebookId.equals(candidate.notebookId())) {
                chosenNotebook = candidate;
                break;
            }
        }

        if (chosenNotebook != null && topNotebook.notebookId().equals(chosenNotebook.notebookId())) {
            return null;
        }

        if (topNotebook.score() < 0.35) {
            return null;
        }

        double chosenNotebookScore = chosenNotebook == null ? 0.0 : chosenNotebook.score();
        if (topNotebook.score() - chosenNotebookScore < 0.20) {
            return null;
        }

        NoteCandidate chosenNote = null;
        for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
            if (targetNoteId != null && targetNoteId.equals(candidate.noteId())) {
                chosenNote = candidate;
                break;
            }
        }

        if (targetNoteId != null && chosenNote != null && chosenNote.score() > 0.35) {
            return null;
        }

        List<String> updatedReasonCodes = new ArrayList<>(reasonCodes);
        if (!updatedReasonCodes.contains("notebook_match_override")) {
            updatedReasonCodes.add("notebook_match_override");
        }

        RouteStrategy redirectedStrategy = lowSpecificityCapture
            ? RouteStrategy.NOTEBOOK_INBOX
            : RouteStrategy.DIRECT_APPLY;

        return new RoutePlanV1(
            RouteIntent.CREATE_NOTE,
            topNotebook.notebookId(),
            null,
            targetNoteType == null || targetNoteType.isBlank() ? CanonicalNoteTemplates.GENERIC_NOTE : targetNoteType,
            Math.max(confidence, topNotebook.score()),
            sanitizeReasonCodes(updatedReasonCodes),
            redirectedStrategy,
            null
        );
    }

    private RoutePlanV1 maybeRedirectSelectedNoteCapture(RouteRequestContext context,
                                                         RoutePlanV1 raw,
                                                         RouteIntent intent,
                                                         Long targetNotebookId,
                                                         Long targetNoteId,
                                                         String targetNoteType,
                                                         double confidence,
                                                         List<String> reasonCodes,
                                                         NoteCandidate explicitMentionedNote,
                                                         boolean explicitCreateRequest,
                                                         boolean selectedNoteUpdate,
                                                         boolean questionLikeMessage) {
        if (context.selectedNoteId() == null
            || explicitMentionedNote != null
            || explicitCreateRequest
            || selectedNoteUpdate
            || questionLikeMessage
            || targetNoteId == null
            || !targetNoteId.equals(context.selectedNoteId())
            || intent != RouteIntent.WRITE_EXISTING_NOTE) {
            return null;
        }

        NotebookCandidate topNotebook = context.retrievalBundle().notebookCandidates().isEmpty()
            ? null
            : context.retrievalBundle().notebookCandidates().getFirst();
        NoteCandidate selectedCandidate = null;
        for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
            if (targetNoteId.equals(candidate.noteId())) {
                selectedCandidate = candidate;
                break;
            }
        }

        if (topNotebook == null
            || targetNotebookId == null
            || topNotebook.notebookId().equals(targetNotebookId)
            || selectedCandidate == null) {
            return null;
        }

        if (topNotebook.score() < 0.30 || selectedCandidate.score() > 0.35) {
            return null;
        }

        List<String> updatedReasonCodes = new ArrayList<>(reasonCodes);
        if (!updatedReasonCodes.contains("selected_note_mismatch")) {
            updatedReasonCodes.add("selected_note_mismatch");
        }

        return new RoutePlanV1(
            RouteIntent.CREATE_NOTE,
            topNotebook.notebookId(),
            null,
            targetNoteType == null || targetNoteType.isBlank() ? CanonicalNoteTemplates.GENERIC_NOTE : targetNoteType,
            Math.max(confidence, topNotebook.score()),
            sanitizeReasonCodes(updatedReasonCodes),
            RouteStrategy.DIRECT_APPLY,
            null
        );
    }

    private RoutePlanV1 maybePromoteConcreteNotebookCapture(RouteRequestContext context,
                                                            RouteIntent intent,
                                                            Long targetNotebookId,
                                                            Long targetNoteId,
                                                            String targetNoteType,
                                                            double confidence,
                                                            List<String> reasonCodes,
                                                            RouteStrategy strategy,
                                                            NoteCandidate explicitMentionedNote,
                                                            boolean explicitCreateRequest,
                                                            boolean lowSpecificityCapture,
                                                            boolean questionLikeMessage) {
        if (explicitMentionedNote != null
            || explicitCreateRequest
            || lowSpecificityCapture
            || questionLikeMessage
            || targetNotebookId == null
            || intent != RouteIntent.CREATE_NOTE
            || strategy != RouteStrategy.NOTEBOOK_INBOX) {
            return null;
        }

        NoteCandidate strongestNotebookNote = strongestNotebookNote(context, targetNotebookId);
        List<String> updatedReasonCodes = new ArrayList<>(reasonCodes);
        if (!updatedReasonCodes.contains("concrete_capture")) {
            updatedReasonCodes.add("concrete_capture");
        }

        if (strongestNotebookNote != null && strongestNotebookNote.score() >= 0.45) {
            if (!updatedReasonCodes.contains("retrieval_note_match")) {
                updatedReasonCodes.add("retrieval_note_match");
            }
            return new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                strongestNotebookNote.notebookId(),
                strongestNotebookNote.noteId(),
                strongestNotebookNote.noteType(),
                Math.max(confidence, strongestNotebookNote.score()),
                sanitizeReasonCodes(updatedReasonCodes),
                RouteStrategy.DIRECT_APPLY,
                null
            );
        }

        if (!updatedReasonCodes.contains("direct_capture_create")) {
            updatedReasonCodes.add("direct_capture_create");
        }
        return new RoutePlanV1(
            RouteIntent.CREATE_NOTE,
            targetNotebookId,
            targetNoteId,
            targetNoteType == null || targetNoteType.isBlank() ? CanonicalNoteTemplates.GENERIC_NOTE : targetNoteType,
            Math.max(confidence, aiProperties.getRouteAutoApplyThreshold()),
            sanitizeReasonCodes(updatedReasonCodes),
            RouteStrategy.DIRECT_APPLY,
            null
        );
    }

    private NoteCandidate strongestNotebookNote(RouteRequestContext context, Long notebookId) {
        if (notebookId == null) {
            return null;
        }
        for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
            if (notebookId.equals(candidate.notebookId())) {
                return candidate;
            }
        }
        return null;
    }

    private NoteCandidate topNoteCandidate(RouteRequestContext context) {
        if (context == null
            || context.retrievalBundle() == null
            || context.retrievalBundle().noteCandidates().isEmpty()) {
            return null;
        }
        return context.retrievalBundle().noteCandidates().getFirst();
    }

    private Double topNoteGap(RetrievalBundle retrievalBundle) {
        if (retrievalBundle == null || retrievalBundle.noteCandidates().size() < 2) {
            return null;
        }
        return retrievalBundle.noteCandidates().get(0).score() - retrievalBundle.noteCandidates().get(1).score();
    }

    private boolean isAmbiguousNoteGap(NoteCandidate topNote, Double topNoteGap) {
        if (topNote == null) {
            return true;
        }
        if (topNote.exactTitleMatch()) {
            return false;
        }
        if (topNote.entityTags() != null && !topNote.entityTags().isEmpty()
            && "active".equals(topNote.activityStatus())
            && topNote.score() >= aiProperties.getRouteAutoApplyThreshold()) {
            return false;
        }
        if (topNote.score() < aiProperties.getRouteAutoApplyThreshold() + 0.03) {
            return true;
        }
        return topNoteGap != null && topNoteGap < 0.06;
    }

    private boolean hasStrongTargetEvidence(RouteRequestContext context,
                                            NoteCandidate targetNote,
                                            NoteCandidate explicitMentionedNote,
                                            boolean selectedNoteUpdate) {
        if (targetNote == null) {
            return false;
        }
        if (explicitMentionedNote != null && targetNote.noteId().equals(explicitMentionedNote.noteId())) {
            return true;
        }
        if (selectedNoteUpdate && context.selectedNoteId() != null && context.selectedNoteId().equals(targetNote.noteId())) {
            return true;
        }
        if (targetNote.exactTitleMatch() || mentionsNoteTitle(context.message(), targetNote.title())) {
            return true;
        }
        if (hasEntityTagOverlap(context.message(), targetNote) && "active".equals(targetNote.activityStatus())) {
            return true;
        }
        return false;
    }

    private boolean hasEntityTagOverlap(String message, NoteCandidate note) {
        if (message == null || message.isBlank() || note.entityTags() == null || note.entityTags().isEmpty()) {
            return false;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        for (String tag : note.entityTags()) {
            if (tag != null && !tag.isBlank() && normalizedMessage.contains(tag.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adjusts the router's self-reported confidence using multiple retrieval signals.
     * Boosts confidence when strong evidence exists; penalizes when signals conflict.
     */
    private double adjustConfidenceMultiSignal(RouteRequestContext context, double rawConfidence,
                                               Long targetNoteId, Long targetNotebookId,
                                               NoteCandidate explicitMentionedNote,
                                               boolean selectedNoteUpdate) {
        double adjusted = rawConfidence;
        NoteCandidate topNote = topNoteCandidate(context);
        Double gap = topNoteGap(context.retrievalBundle());

        // Boost: explicit mention or selected note match
        if (explicitMentionedNote != null) {
            adjusted = Math.max(adjusted, 0.90);
        } else if (selectedNoteUpdate) {
            adjusted = Math.max(adjusted, 0.85);
        }

        // Boost: exact title match on top candidate aligning with target
        if (topNote != null && topNote.exactTitleMatch()
            && targetNoteId != null && targetNoteId.equals(topNote.noteId())) {
            adjusted = Math.max(adjusted, 0.88);
        }

        // Boost: entity tag overlap on active note
        if (targetNoteId != null && topNote != null && targetNoteId.equals(topNote.noteId())
            && hasEntityTagOverlap(context.message(), topNote)
            && "active".equals(topNote.activityStatus())) {
            adjusted += 0.05;
        }

        // Penalize: target doesn't match top retrieval candidate
        if (targetNoteId != null && topNote != null && !targetNoteId.equals(topNote.noteId())) {
            adjusted -= 0.10;
        }

        // Penalize: narrow gap between top two candidates (ambiguity)
        if (gap != null && gap < 0.06 && topNote != null && !topNote.exactTitleMatch()) {
            adjusted -= 0.08;
        }

        // Boost: wide gap means clear winner
        if (gap != null && gap > 0.15 && topNote != null
            && targetNoteId != null && targetNoteId.equals(topNote.noteId())) {
            adjusted += 0.05;
        }

        return clamp(adjusted);
    }

    private boolean shouldPreferNotebookInbox(RouteRequestContext context,
                                              RouteIntent intent,
                                              RouteStrategy strategy,
                                              Long targetNoteId,
                                              List<String> reasonCodes,
                                              Map<Long, NoteCandidate> allowedNotes,
                                              boolean hasExplicitMentionedNote) {
        if (intent != RouteIntent.WRITE_EXISTING_NOTE
            || strategy != RouteStrategy.NOTE_INBOX
            || targetNoteId == null
            || !isLowSpecificityCapture(context.message())
            || hasExplicitMentionedNote) {
            return false;
        }

        NoteCandidate targetNote = allowedNotes.get(targetNoteId);
        return targetNote == null || !mentionsNoteTitle(context.message(), targetNote.title());
    }

    private boolean isExplicitCreateRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("create a note")
            || normalized.contains("create note")
            || normalized.contains("new note")
            || normalized.contains("project note")
            || normalized.contains("entity log")
            || normalized.contains("entity note")
            || normalized.contains("reference note")
            || normalized.contains("reference doc")
            || normalized.contains("note called")
            || normalized.contains("start a note");
    }

    private String requestedCreateNoteType(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("project note")) {
            return CanonicalNoteTemplates.PROJECT_NOTE;
        }
        if (normalized.contains("entity log") || normalized.contains("entity note")) {
            return CanonicalNoteTemplates.ENTITY_LOG;
        }
        if (normalized.contains("reference note") || normalized.contains("reference doc")) {
            return CanonicalNoteTemplates.REFERENCE_NOTE;
        }
        if (normalized.contains("create a note")
            || normalized.contains("create note")
            || normalized.contains("new note")) {
            return CanonicalNoteTemplates.GENERIC_NOTE;
        }
        return null;
    }

    private boolean isLowSpecificityCapture(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        int wordCount = normalized.trim().split("\\s+").length;
        if (wordCount > 8) {
            return false;
        }
        return normalized.contains("remember")
            || normalized.contains("later")
            || normalized.contains("sometime")
            || normalized.contains("at some point")
            || normalized.contains("don't forget")
            || normalized.contains("dont forget");
    }

    private boolean mentionsNoteTitle(String message, String noteTitle) {
        if (message == null || message.isBlank() || noteTitle == null || noteTitle.isBlank()) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(noteTitle.toLowerCase(Locale.ROOT));
    }

    private NoteCandidate findExplicitMentionedNote(String message, java.util.Collection<NoteCandidate> candidates) {
        if (message == null || message.isBlank() || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String normalizedMessage = message.toLowerCase(Locale.ROOT);
        NoteCandidate bestMatch = null;
        int bestLength = -1;
        for (NoteCandidate candidate : candidates) {
            String title = candidate.title();
            if (title == null || title.isBlank()) {
                continue;
            }
            String normalizedTitle = title.toLowerCase(Locale.ROOT);
            if (!normalizedMessage.contains(normalizedTitle)) {
                continue;
            }
            if (normalizedTitle.length() > bestLength) {
                bestMatch = candidate;
                bestLength = normalizedTitle.length();
            }
        }
        return bestMatch;
    }

    private boolean looksLikeQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).trim();
        return normalized.endsWith("?")
            || normalized.startsWith("what ")
            || normalized.startsWith("what's ")
            || normalized.startsWith("whats ")
            || normalized.startsWith("which ")
            || normalized.startsWith("where ")
            || normalized.startsWith("when ")
            || normalized.startsWith("why ")
            || normalized.startsWith("how ")
            || normalized.startsWith("does ")
            || normalized.startsWith("do ")
            || normalized.startsWith("is ")
            || normalized.startsWith("are ");
    }

    private boolean shouldStickToSelectedNote(RouteRequestContext context,
                                              NoteCandidate explicitMentionedNote,
                                              boolean explicitCreateRequest) {
        if (context.selectedNoteId() == null
            || explicitMentionedNote != null
            || explicitCreateRequest
            || looksLikeQuestion(context.message())
            || isLowSpecificityCapture(context.message())) {
            return false;
        }
        return looksLikeSectionScopedUpdate(context.message());
    }

    private boolean looksLikeSectionScopedUpdate(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).trim();
        return normalized.startsWith("status update")
            || normalized.startsWith("decision:")
            || normalized.startsWith("open question:")
            || normalized.startsWith("add a task")
            || normalized.startsWith("add task")
            || normalized.startsWith("add an action item")
            || normalized.startsWith("add action item")
            || normalized.startsWith("add a timeline event")
            || normalized.startsWith("add timeline")
            || normalized.startsWith("save this reference")
            || normalized.startsWith("add a reference")
            || normalized.startsWith("save this docs link")
            || normalized.startsWith("rewrite this note")
            || normalized.startsWith("replace the status")
            || normalized.startsWith("add to the body")
            || normalized.startsWith("add this observation")
            || normalized.startsWith("rename this note");
    }

    private PatchPlanV1 sanitizePatchPlan(PatchRequestContext context, PatchPlanV1 raw) {
        if (context.routePlan().intent() == RouteIntent.ANSWER_ONLY || context.routePlan().intent() == RouteIntent.CLARIFY) {
            return new PatchPlanV1(
                context.routePlan().targetNotebookId(),
                context.routePlan().targetNoteId(),
                context.routePlan().targetNoteType(),
                List.of(),
                false,
                plannerPromptVersion()
            );
        }

        List<PatchOperation> normalizedOps = new ArrayList<>();
        if (raw.ops() != null) {
            for (PatchOperation operation : raw.ops()) {
                if (operation == null || operation.op() == null) {
                    continue;
                }
                PatchOperation normalized = normalizeOperation(context, operation);
                if (normalized != null) {
                    normalizedOps.add(normalized);
                }
            }
        }
        if (normalizedOps.isEmpty()) {
            PatchOperation fallbackOperation = fallbackOperation(context);
            if (fallbackOperation != null) {
                normalizedOps.add(fallbackOperation);
            }
        }
        normalizedOps = ensureCreateNoteOp(context, normalizedOps);
        normalizedOps = repairVisibleSummaryOps(context, normalizedOps);
        normalizedOps = removeDuplicateAppendOps(context, normalizedOps);

        boolean fallbackToInbox = context.routePlan().strategy() == RouteStrategy.NOTE_INBOX
            || context.routePlan().strategy() == RouteStrategy.NOTEBOOK_INBOX;

        return new PatchPlanV1(
            context.routePlan().targetNotebookId(),
            context.routePlan().targetNoteId(),
            context.routePlan().targetNoteType(),
            normalizedOps,
            fallbackToInbox,
            plannerPromptVersion()
        );
    }

    private List<PatchOperation> repairVisibleSummaryOps(PatchRequestContext context, List<PatchOperation> ops) {
        if (!requestsVisibleSummary(context.message())
            || !targetHasSection(context, "summary")
            || hasSectionWrite(ops, "summary")) {
            return ops;
        }

        String summarySource = ops.stream()
            .filter(operation -> operation.op() == PatchOpType.UPDATE_NOTE_SUMMARY)
            .map(PatchOperation::summaryShort)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse(null);
        if (summarySource == null || summarySource.isBlank()) {
            return ops;
        }

        List<PatchOperation> repaired = new ArrayList<>(ops);
        repaired.add(new PatchOperation(
            hasExistingContent(context, "summary") ? PatchOpType.REPLACE_SECTION_CONTENT : PatchOpType.APPEND_SECTION_CONTENT,
            "summary",
            null,
            null,
            trimToLength(summarySource, 160),
            normalizeContentForSection(summarySource, "summary", context.message()),
            null
        ));
        return repaired;
    }

    private PatchPlanV1 fallbackPatchPlan(PatchRequestContext context) {
        PatchOperation fallbackOperation = fallbackOperation(context);
        List<PatchOperation> ops = fallbackOperation == null ? List.of() : List.of(fallbackOperation);
        boolean fallbackToInbox = context.routePlan().strategy() == RouteStrategy.NOTE_INBOX
            || context.routePlan().strategy() == RouteStrategy.NOTEBOOK_INBOX;
        return new PatchPlanV1(
            context.routePlan().targetNotebookId(),
            context.routePlan().targetNoteId(),
            context.routePlan().targetNoteType(),
            ops,
            fallbackToInbox,
            plannerPromptVersion()
        );
    }

    private PatchOperation normalizeOperation(PatchRequestContext context, PatchOperation operation) {
        PatchOpType opType = operation.op();
        String forcedInboxSectionId = context.routePlan().strategy() == RouteStrategy.NOTE_INBOX
            || context.routePlan().strategy() == RouteStrategy.NOTEBOOK_INBOX
            ? "inbox"
            : null;

        String sectionId = normalizeSectionIdForTarget(
            forceInboxIfNeeded(operation.sectionId(), forcedInboxSectionId, opType),
            context
        );
        String afterSectionId = normalizeSectionIdForTarget(
            forceInboxIfNeeded(operation.afterSectionId(), forcedInboxSectionId, opType),
            context
        );
        String title = trimToLength(operation.title(), 200);
        String summaryShort = trimToLength(operation.summaryShort(), 160);
        String contentMarkdown = trimMarkdown(operation.contentMarkdown(), 12000);
        List<NoteSection> sections = normalizeSections(operation.sections());

        if (CanonicalNoteTemplates.GENERIC_NOTE.equals(context.routePlan().targetNoteType())
            && ("action_items".equalsIgnoreCase(sectionId) || "tasks".equalsIgnoreCase(sectionId))
            && prefersGenericBody(context.message())) {
            sectionId = "body";
        }

        String normalizedSectionId = sectionId == null ? null : sectionId.trim().toLowerCase(Locale.ROOT);

        if (opType == PatchOpType.UPDATE_NOTE_SUMMARY && (summaryShort == null || summaryShort.isBlank())) {
            summaryShort = trimToLength(contentMarkdown, 160);
        }
        if ((opType == PatchOpType.APPEND_SECTION_CONTENT || opType == PatchOpType.REPLACE_SECTION_CONTENT)
            && (contentMarkdown == null || contentMarkdown.isBlank())
            && ("summary".equals(normalizedSectionId) || "status".equals(normalizedSectionId) || "body".equals(normalizedSectionId))) {
            contentMarkdown = summaryShort;
        }

        if (opType == PatchOpType.REPLACE_SECTION_CONTENT || opType == PatchOpType.APPEND_SECTION_CONTENT) {
            sectionId = forcedInboxSectionId != null ? forcedInboxSectionId : sectionId;
        }

        if (opType == PatchOpType.CREATE_NOTE && context.routePlan().strategy() == RouteStrategy.NOTEBOOK_INBOX) {
            String fallbackContent = firstPresent(operation.contentMarkdown(), operation.summaryShort(), operation.title(), context.message());
            return new PatchOperation(
                PatchOpType.APPEND_SECTION_CONTENT,
                "inbox",
                null,
                null,
                summaryShort,
                normalizeContentForSection(fallbackContent, "inbox", context.message()),
                null
            );
        }

        String normalizedContent = normalizeContentForSection(contentMarkdown, sectionId, context.message());
        normalizedContent = ensureProtectedTokens(normalizedContent, sectionId, context.message());

        PatchOperation normalized = new PatchOperation(
            opType,
            sectionId,
            afterSectionId,
            title,
            summaryShort,
            normalizedContent,
            sections
        );
        return isUsableOperation(normalized) ? normalized : null;
    }

    private PatchOperation fallbackOperation(PatchRequestContext context) {
        if (context.routePlan().intent() != RouteIntent.WRITE_EXISTING_NOTE
            && context.routePlan().intent() != RouteIntent.CREATE_NOTE) {
            return null;
        }
        String sectionId = fallbackSectionId(context);
        if (sectionId == null || sectionId.isBlank()) {
            return null;
        }
        String content = fallbackContent(sectionId, context.message());
        if (content == null || content.isBlank()) {
            return null;
        }
        return new PatchOperation(
            PatchOpType.APPEND_SECTION_CONTENT,
            sectionId,
            null,
            null,
            trimToLength(content, 160),
            normalizeContentForSection(content, sectionId, context.message()),
            null
        );
    }

    private List<PatchOperation> ensureCreateNoteOp(PatchRequestContext context, List<PatchOperation> ops) {
        if (context.routePlan().intent() != RouteIntent.CREATE_NOTE
            || context.routePlan().strategy() != RouteStrategy.DIRECT_APPLY) {
            return ops;
        }
        boolean hasCreate = ops.stream().anyMatch(operation -> operation.op() == PatchOpType.CREATE_NOTE);
        if (hasCreate) {
            return ops;
        }

        List<PatchOperation> updated = new ArrayList<>();
        updated.add(new PatchOperation(
            PatchOpType.CREATE_NOTE,
            null,
            null,
            inferCreateTitle(context.message(), context.routePlan().targetNoteType()),
            trimToLength(context.message(), 160),
            null,
            null
        ));
        updated.addAll(ops);
        return updated;
    }

    private String inferCreateTitle(String message, String noteType) {
        String explicitTitle = extractExplicitCreateTitle(message);
        if (explicitTitle != null) {
            return explicitTitle;
        }
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "toddler", "baby", "child", "kid", "family")) {
            return "Family observation";
        }
        if (containsAny(normalized, "dog", "puppy", "pet", "cat", "vet")) {
            return "Pet observation";
        }
        if (CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType)) {
            return "New project note";
        }
        return "New note";
    }

    private String extractExplicitCreateTitle(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)(?:called|named|title[d]?\\s+)([A-Za-z0-9][^.,\\n]*)").matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String title = matcher.group(1).trim();
        return title.isBlank() ? null : trimToLength(title, 200);
    }

    private String fallbackSectionId(PatchRequestContext context) {
        if (context.routePlan().strategy() == RouteStrategy.NOTE_INBOX
            || context.routePlan().strategy() == RouteStrategy.NOTEBOOK_INBOX) {
            return "inbox";
        }
        String normalized = context.message() == null ? "" : context.message().toLowerCase(Locale.ROOT).trim();
        boolean projectNote = CanonicalNoteTemplates.PROJECT_NOTE.equals(context.routePlan().targetNoteType());

        if (normalized.startsWith("decision:")) {
            return projectNote ? "decisions" : "body";
        }
        if (normalized.startsWith("open question:")) {
            return projectNote ? "open_questions" : "body";
        }
        if (normalized.startsWith("status update") || normalized.startsWith("status:")) {
            return projectNote ? "status" : "body";
        }
        if (normalized.contains("timeline event")) {
            return projectNote ? "timeline" : "body";
        }
        if (containsUrl(normalized) || normalized.startsWith("save this reference") || normalized.startsWith("add a reference")) {
            return "references";
        }
        if (normalized.startsWith("add a task")
            || normalized.startsWith("add task")
            || normalized.startsWith("add an action item")
            || normalized.startsWith("add action item")
            || normalized.startsWith("todo:")) {
            return projectNote ? "tasks" : "action_items";
        }
        return projectNote ? "status" : "body";
    }

    private String fallbackContent(String sectionId, String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String trimmed = message.trim();
        String normalizedSectionId = sectionId.toLowerCase(Locale.ROOT);
        return switch (normalizedSectionId) {
            case "tasks", "action_items" -> stripLeadingCue(trimmed,
                "add a task to ", "add task to ", "add an action item to ", "add action item to ", "todo:");
            case "decisions" -> stripLeadingCue(trimmed, "decision:");
            case "open_questions" -> stripLeadingCue(trimmed, "open question:");
            case "status" -> stripLeadingCue(trimmed, "status update:", "status:");
            case "timeline" -> stripLeadingCue(trimmed, "add a timeline event for ", "timeline event:");
            case "references" -> {
                String url = firstUrl(trimmed);
                yield url != null ? url : stripLeadingCue(trimmed, "save this reference:", "save this docs link:", "add a reference:");
            }
            case "inbox" -> trimmed;
            default -> stripLeadingCue(trimmed, "add that ", "add this observation to the body exactly:", "add this observation to the body:", "add this observation:", "add to the body:");
        };
    }

    private String normalizeSectionIdForTarget(String requestedSectionId, PatchRequestContext context) {
        if (requestedSectionId == null || requestedSectionId.isBlank()) {
            return requestedSectionId;
        }

        Set<String> allowedSectionIds = new HashSet<>();
        for (NoteSection section : context.targetDocument().sections()) {
            allowedSectionIds.add(section.id());
        }
        if (allowedSectionIds.contains(requestedSectionId)) {
            return requestedSectionId;
        }

        String normalized = requestedSectionId.trim().toLowerCase(Locale.ROOT);
        if (allowedSectionIds.contains(normalized)) {
            return normalized;
        }

        if (CanonicalNoteTemplates.GENERIC_NOTE.equals(context.routePlan().targetNoteType())) {
            return switch (normalized) {
                case "tasks" -> "action_items";
                case "status", "decisions", "open_questions", "timeline" -> "body";
                default -> allowedSectionIds.contains("body") ? "body" : normalized;
            };
        }

        return switch (normalized) {
            case "action_items" -> "tasks";
            case "body" -> "status";
            default -> allowedSectionIds.contains("summary") ? "summary" : normalized;
        };
    }

    private List<NoteSection> normalizeSections(List<NoteSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return sections;
        }
        Set<String> seenIds = new LinkedHashSet<>();
        List<NoteSection> normalized = new ArrayList<>();
        for (NoteSection section : sections) {
            if (section == null || section.id() == null || section.id().isBlank()) {
                continue;
            }
            String id = section.id().trim();
            if (!seenIds.add(id)) {
                continue;
            }
            String label = trimToLength(section.label(), 120);
            String kind = trimToLength(section.kind(), 64);
            normalized.add(new NoteSection(
                id,
                label == null || label.isBlank() ? id : label,
                kind == null || kind.isBlank() ? "body" : kind,
                trimMarkdown(section.contentMarkdown(), 12000)
            ));
        }
        return normalized;
    }

    private String normalizeContentForSection(String contentMarkdown, String sectionId, String originalMessage) {
        if (contentMarkdown == null || contentMarkdown.isBlank()) {
            return null;
        }
        if (sectionId == null || sectionId.isBlank()) {
            return contentMarkdown;
        }
        String trimmed = contentMarkdown.trim();
        String normalizedSectionId = sectionId.toLowerCase(Locale.ROOT);

        if ("tasks".equals(normalizedSectionId) || "action_items".equals(normalizedSectionId)) {
            return normalizeChecklist(trimmed, originalMessage);
        }
        if ("summary".equals(normalizedSectionId) || "status".equals(normalizedSectionId) || "body".equals(normalizedSectionId)) {
            return normalizeProse(trimmed, originalMessage);
        }
        if ("decisions".equals(normalizedSectionId) || "open_questions".equals(normalizedSectionId)
            || "timeline".equals(normalizedSectionId) || "references".equals(normalizedSectionId)
            || "inbox".equals(normalizedSectionId)) {
            return normalizeBullet(trimmed, originalMessage);
        }
        return trimmed;
    }

    private boolean prefersGenericBody(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("add a note")
            || normalized.contains("add note")
            || normalized.contains("add that")
            || normalized.contains("add this observation")
            || normalized.contains("add to the body");
    }

    private String stripLeadingCue(String value, String... cues) {
        String trimmed = value == null ? null : value.trim();
        if (trimmed == null || trimmed.isBlank()) {
            return trimmed;
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        for (String cue : cues) {
            String normalizedCue = cue.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(normalizedCue)) {
                String stripped = trimmed.substring(cue.length()).trim();
                return stripped.isBlank() ? trimmed : stripped;
            }
        }
        return trimmed;
    }

    private boolean containsUrl(String value) {
        return firstUrl(value) != null;
    }

    private String firstUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://\\S+").matcher(value);
        return matcher.find() ? matcher.group() : null;
    }

    private String normalizeProse(String contentMarkdown, String originalMessage) {
        if (contentMarkdown == null || contentMarkdown.isBlank()) {
            return null;
        }
        if (requestsListFormatting(originalMessage)) {
            return contentMarkdown;
        }

        List<String> parts = new ArrayList<>();
        for (String line : contentMarkdown.split("\\R")) {
            String normalized = line.trim();
            if (normalized.isBlank()) {
                continue;
            }
            normalized = normalized
                .replaceFirst("^[-*]\\s+\\[ \\]\\s+", "")
                .replaceFirst("^[-*]\\s+", "");
            if (!normalized.isBlank()) {
                parts.add(normalized);
            }
        }

        if (parts.isEmpty()) {
            return contentMarkdown.trim();
        }
        if (parts.size() == 1 && !contentMarkdown.trim().startsWith("-") && !contentMarkdown.trim().startsWith("*")) {
            return parts.get(0);
        }
        return String.join(" ", parts);
    }

    private String normalizeChecklist(String contentMarkdown, String originalMessage) {
        String firstLine = firstNonBlankLine(contentMarkdown);
        if (firstLine.startsWith("- [ ] ") || firstLine.startsWith("* [ ] ")) {
            return contentMarkdown.replace("* [ ] ", "- [ ] ");
        }
        String source = firstLine.startsWith("- ") ? firstLine.substring(2).trim() : firstLine;
        if (source.isBlank()) {
            source = originalMessage == null ? "" : originalMessage.trim();
        }
        return "- [ ] " + stripTerminalPunctuation(source);
    }

    private String normalizeBullet(String contentMarkdown, String originalMessage) {
        String firstLine = firstNonBlankLine(contentMarkdown);
        if (firstLine.startsWith("- ")) {
            return contentMarkdown;
        }
        String source = firstLine.isBlank() ? originalMessage : firstLine;
        return "- " + stripTerminalPunctuation(source == null ? "" : source.trim());
    }

    private String firstNonBlankLine(String value) {
        for (String line : value.split("\\R")) {
            if (!line.isBlank()) {
                return line.trim();
            }
        }
        return "";
    }

    private String stripTerminalPunctuation(String value) {
        return value.replaceAll("[.\\s]+$", "").trim();
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean containsAny(String text, String... values) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean requestsListFormatting(String originalMessage) {
        if (originalMessage == null || originalMessage.isBlank()) {
            return false;
        }
        String normalized = originalMessage.toLowerCase(Locale.ROOT);
        return normalized.contains("bullet")
            || normalized.contains("bulleted")
            || normalized.contains("list")
            || normalized.contains("outline");
    }

    private boolean isUsableOperation(PatchOperation operation) {
        if (operation == null || operation.op() == null) {
            return false;
        }
        return switch (operation.op()) {
            case CREATE_NOTE -> operation.title() != null && !operation.title().isBlank();
            case RENAME_NOTE -> operation.title() != null && !operation.title().isBlank();
            case UPDATE_NOTE_SUMMARY -> operation.summaryShort() != null && !operation.summaryShort().isBlank();
            case APPEND_SECTION_CONTENT, REPLACE_SECTION_CONTENT ->
                operation.sectionId() != null && !operation.sectionId().isBlank()
                    && operation.contentMarkdown() != null && !operation.contentMarkdown().isBlank();
            case INSERT_SECTION_AFTER ->
                operation.afterSectionId() != null && !operation.afterSectionId().isBlank()
                    && operation.sections() != null && !operation.sections().isEmpty();
            case DELETE_SECTION -> operation.sectionId() != null && !operation.sectionId().isBlank();
            case REPLACE_NOTE_OUTLINE -> operation.sections() != null && !operation.sections().isEmpty();
        };
    }

    private String forceInboxIfNeeded(String value, String forcedInboxSectionId, PatchOpType opType) {
        if (forcedInboxSectionId == null) {
            return value;
        }
        if (opType == PatchOpType.APPEND_SECTION_CONTENT || opType == PatchOpType.REPLACE_SECTION_CONTENT) {
            return forcedInboxSectionId;
        }
        return value;
    }

    private boolean requestsVisibleSummary(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return normalized.contains("summary")
            && (normalized.contains("add")
            || normalized.contains("write")
            || normalized.contains("update")
            || normalized.contains("replace")
            || normalized.contains("short"));
    }

    private boolean targetHasSection(PatchRequestContext context, String sectionId) {
        for (NoteSection section : context.targetDocument().sections()) {
            if (section.id().equals(sectionId)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExistingContent(PatchRequestContext context, String sectionId) {
        for (NoteSection section : context.targetDocument().sections()) {
            if (section.id().equals(sectionId)) {
                return section.contentMarkdown() != null && !section.contentMarkdown().isBlank();
            }
        }
        return false;
    }

    private boolean hasSectionWrite(List<PatchOperation> ops, String sectionId) {
        for (PatchOperation operation : ops) {
            if ((operation.op() == PatchOpType.APPEND_SECTION_CONTENT || operation.op() == PatchOpType.REPLACE_SECTION_CONTENT)
                && sectionId.equals(operation.sectionId())) {
                return true;
            }
        }
        return false;
    }

    private List<PatchOperation> removeDuplicateAppendOps(PatchRequestContext context, List<PatchOperation> ops) {
        if (ops == null || ops.isEmpty()) {
            return ops;
        }
        List<PatchOperation> deduplicated = new ArrayList<>();
        Set<String> seenAppends = new LinkedHashSet<>();
        for (PatchOperation operation : ops) {
            if (operation.op() != PatchOpType.APPEND_SECTION_CONTENT || operation.sectionId() == null) {
                deduplicated.add(operation);
                continue;
            }
            String normalizedCandidate = comparableMarkdown(operation.contentMarkdown());
            if (normalizedCandidate.isBlank()) {
                continue;
            }
            String duplicateKey = operation.sectionId() + "::" + normalizedCandidate;
            if (!seenAppends.add(duplicateKey)) {
                continue;
            }
            String existingSectionContent = currentSectionContent(context, operation.sectionId());
            if (comparableMarkdown(existingSectionContent).contains(normalizedCandidate)) {
                continue;
            }
            deduplicated.add(operation);
        }
        return deduplicated;
    }

    private String currentSectionContent(PatchRequestContext context, String sectionId) {
        if (context == null || sectionId == null) {
            return "";
        }
        for (NoteSection section : context.targetDocument().sections()) {
            if (sectionId.equals(section.id())) {
                return section.contentMarkdown();
            }
        }
        return "";
    }

    private String ensureProtectedTokens(String contentMarkdown, String sectionId, String originalMessage) {
        if (contentMarkdown == null || contentMarkdown.isBlank() || originalMessage == null || originalMessage.isBlank()) {
            return contentMarkdown;
        }
        List<String> protectedTokens = extractProtectedTokens(originalMessage);
        if (protectedTokens.isEmpty()) {
            return contentMarkdown;
        }
        List<String> missingTokens = protectedTokens.stream()
            .filter(token -> !containsIgnoringCase(contentMarkdown, token))
            .toList();
        if (missingTokens.isEmpty()) {
            return contentMarkdown;
        }

        String fallback = normalizeContentForSection(fallbackContent(sectionId == null ? "body" : sectionId, originalMessage), sectionId, originalMessage);
        if (fallback != null && !fallback.isBlank()) {
            boolean fallbackHasAll = missingTokens.stream().allMatch(token -> containsIgnoringCase(fallback, token));
            if (fallbackHasAll) {
                return fallback;
            }
        }

        return appendMissingProtectedTokens(contentMarkdown, missingTokens, sectionId);
    }

    private List<String> extractProtectedTokens(String message) {
        LinkedHashSet<String> protectedTokens = new LinkedHashSet<>();
        if (message == null || message.isBlank()) {
            return List.of();
        }
        collectProtectedTokens(protectedTokens, message, "\"([^\"]{2,120})\"");
        collectProtectedTokens(protectedTokens, message, "'([^'\\n]{2,120})'");
        collectProtectedTokens(protectedTokens, message, "(https?://\\S+)");
        collectProtectedTokens(protectedTokens, message, "(\\./\\S+(?:\\s+--[a-z0-9-]+(?:[ =][^\\s]+)?)*)");
        collectProtectedTokens(protectedTokens, message, "\\b\\d{4}-\\d{2}-\\d{2}\\b");
        collectProtectedTokens(protectedTokens, message, "\\b\\d+(?:\\.\\d+)?\\s?(?:mg|ml|g|kg|lb|lbs|c|f|%|am|pm)\\b");
        collectProtectedTokens(protectedTokens, message, "\\b[a-z0-9_.-]*\\d{4}-\\d{2}-\\d{2}[a-z0-9_.-]*\\b");
        collectProtectedTokens(protectedTokens, message, "\\b\\d+(?:\\.\\d+)?[CF]\\b");
        return protectedTokens.stream()
            .filter(token -> token != null && !token.isBlank())
            .map(String::trim)
            .toList();
    }

    private void collectProtectedTokens(Set<String> protectedTokens, String source, String regex) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.CASE_INSENSITIVE).matcher(source);
        while (matcher.find()) {
            String token = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
            if (token != null && !token.isBlank()) {
                protectedTokens.add(token.trim());
            }
        }
    }

    private String appendMissingProtectedTokens(String contentMarkdown, List<String> missingTokens, String sectionId) {
        if (missingTokens == null || missingTokens.isEmpty()) {
            return contentMarkdown;
        }
        String suffix = String.join(", ", missingTokens);
        String normalizedSectionId = sectionId == null ? "" : sectionId.toLowerCase(Locale.ROOT);
        if ("tasks".equals(normalizedSectionId) || "action_items".equals(normalizedSectionId)) {
            return stripTerminalPunctuation(contentMarkdown) + " (" + suffix + ")";
        }
        if ("decisions".equals(normalizedSectionId) || "open_questions".equals(normalizedSectionId)
            || "timeline".equals(normalizedSectionId) || "references".equals(normalizedSectionId)
            || "inbox".equals(normalizedSectionId)) {
            return stripTerminalPunctuation(contentMarkdown) + ": " + suffix;
        }
        return contentMarkdown + " " + suffix;
    }

    private String comparableMarkdown(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .toLowerCase(Locale.ROOT)
            .replace("\r", "\n")
            .replaceAll("(?m)^- \\[ \\]\\s*", "")
            .replaceAll("(?m)^- \\[x\\]\\s*", "")
            .replaceAll("(?m)^[-*]\\s*", "")
            .replaceAll("[`*_]", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private boolean containsIgnoringCase(String haystack, String needle) {
        if (haystack == null || needle == null || haystack.isBlank() || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private Map<Long, NoteCandidate> allowedNotes(RouteRequestContext context) {
        java.util.LinkedHashMap<Long, NoteCandidate> allowed = new java.util.LinkedHashMap<>();
        for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
            allowed.put(candidate.noteId(), candidate);
        }
        // Always include the selected note so it can be targeted even if not yet indexed
        if (context.selectedNoteId() != null && !allowed.containsKey(context.selectedNoteId())
            && context.selectedNoteDocument() != null) {
            NoteDocumentV1 doc = context.selectedNoteDocument();
            NoteCandidate selectedAsCandidate = new NoteCandidate(
                context.selectedNoteId(),
                doc.meta().notebookId(),
                doc.meta().title(),
                null,
                doc.meta().noteType(),
                List.of(),
                null,
                null,
                0.0,
                false
            );
            allowed.put(context.selectedNoteId(), selectedAsCandidate);
        }
        return allowed;
    }

    private Set<Long> allowedNotebookIds(RouteRequestContext context) {
        Set<Long> allowed = new LinkedHashSet<>();
        if (context.selectedNotebookId() != null) {
            allowed.add(context.selectedNotebookId());
        }
        if (context.selectedNoteDocument() != null && context.selectedNoteDocument().meta().notebookId() != null) {
            allowed.add(context.selectedNoteDocument().meta().notebookId());
        }
        for (NotebookCandidate candidate : context.retrievalBundle().notebookCandidates()) {
            allowed.add(candidate.notebookId());
        }
        for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
            if (candidate.notebookId() != null) {
                allowed.add(candidate.notebookId());
            }
        }
        return allowed;
    }

    private List<String> sanitizeReasonCodes(List<String> reasonCodes) {
        if (reasonCodes == null || reasonCodes.isEmpty()) {
            return List.of("model_route");
        }
        List<String> normalized = new ArrayList<>();
        for (String code : reasonCodes) {
            if (code == null) {
                continue;
            }
            String trimmed = code.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
            if (!trimmed.isBlank()) {
                normalized.add(trimmed);
            }
            if (normalized.size() >= 4) {
                break;
            }
        }
        return normalized.isEmpty() ? List.of("model_route") : normalized;
    }

    private String extractOutputText(JsonNode body) {
        StringBuilder builder = new StringBuilder();
        for (JsonNode output : body.path("output")) {
            for (JsonNode content : output.path("content")) {
                JsonNode textNode = content.path("text");
                if (textNode.isTextual() && !textNode.asText().isBlank()) {
                    builder.append(textNode.asText());
                }
            }
        }
        if (builder.length() > 0) {
            return builder.toString();
        }
        JsonNode outputText = body.path("output_text");
        if (outputText.isTextual() && !outputText.asText().isBlank()) {
            return outputText.asText();
        }
        return null;
    }

    private void validateCompletedResponse(JsonNode body) {
        String status = body.path("status").asText("");
        if (status.isBlank() || "completed".equalsIgnoreCase(status)) {
            return;
        }
        JsonNode incompleteDetails = body.path("incomplete_details");
        String detail = incompleteDetails.isMissingNode() || incompleteDetails.isNull()
            ? ""
            : trimToLength(incompleteDetails.toString(), 240);
        throw new IllegalStateException("OpenAI response status was " + status + (detail.isBlank() ? "" : ": " + detail));
    }

    private String safeErrorMessage(String responseBody) {
        try {
            JsonNode body = objectMapper.readTree(responseBody);
            JsonNode errorMessage = body.path("error").path("message");
            if (errorMessage.isTextual()) {
                return errorMessage.asText();
            }
        } catch (JsonProcessingException ignored) {
            // Fall through to raw body trimming.
        }
        return trimToLength(responseBody, 400);
    }

    private AiUsageTraceV1 parseUsage(JsonNode usageNode) {
        if (usageNode == null || usageNode.isMissingNode() || usageNode.isNull()) {
            return null;
        }
        Integer inputTokens = intOrNull(usageNode.path("input_tokens"));
        Integer outputTokens = intOrNull(usageNode.path("output_tokens"));
        Integer totalTokens = intOrNull(usageNode.path("total_tokens"));
        Integer reasoningTokens = intOrNull(usageNode.path("output_tokens_details").path("reasoning_tokens"));
        Integer cachedInputTokens = intOrNull(usageNode.path("input_tokens_details").path("cached_tokens"));
        Double estimatedCostUsd = doubleOrNull(usageNode.path("total_cost_usd"));
        if (inputTokens == null
            && outputTokens == null
            && totalTokens == null
            && reasoningTokens == null
            && cachedInputTokens == null
            && estimatedCostUsd == null) {
            return null;
        }
        return new AiUsageTraceV1(
            inputTokens,
            outputTokens,
            totalTokens,
            reasoningTokens,
            cachedInputTokens,
            estimatedCostUsd
        );
    }

    private Integer intOrNull(JsonNode value) {
        return value != null && value.isNumber() ? value.asInt() : null;
    }

    private Double doubleOrNull(JsonNode value) {
        return value != null && value.isNumber() ? value.asDouble() : null;
    }

    private List<String> diffRouteOverrides(RoutePlanV1 raw, RoutePlanV1 sanitized) {
        List<String> overrides = new ArrayList<>();
        if (raw == null || sanitized == null) {
            return overrides;
        }
        if (raw.intent() != sanitized.intent()) {
            overrides.add("intent_changed");
        }
        if (!java.util.Objects.equals(raw.targetNotebookId(), sanitized.targetNotebookId())) {
            overrides.add("target_notebook_changed");
        }
        if (!java.util.Objects.equals(raw.targetNoteId(), sanitized.targetNoteId())) {
            overrides.add("target_note_changed");
        }
        if (!java.util.Objects.equals(raw.targetNoteType(), sanitized.targetNoteType())) {
            overrides.add("target_note_type_changed");
        }
        if (raw.strategy() != sanitized.strategy()) {
            overrides.add("strategy_changed");
        }
        if (!java.util.Objects.equals(trimToLength(raw.answer(), 500), sanitized.answer())) {
            overrides.add("answer_repaired");
        }
        if (Math.abs(raw.confidence() - sanitized.confidence()) >= 0.0001) {
            overrides.add("confidence_adjusted");
        }
        if (!java.util.Objects.equals(raw.reasonCodes(), sanitized.reasonCodes())) {
            overrides.add("reason_codes_normalized");
        }
        return overrides;
    }

    private List<String> diffPatchOverrides(PatchPlanV1 raw, PatchPlanV1 sanitized) {
        List<String> overrides = new ArrayList<>();
        if (raw == null || sanitized == null) {
            return overrides;
        }
        if (raw.fallbackToInbox() != sanitized.fallbackToInbox()) {
            overrides.add("fallback_to_inbox_changed");
        }
        if (raw.ops() == null || raw.ops().isEmpty()) {
            if (sanitized.ops() != null && !sanitized.ops().isEmpty()) {
                overrides.add("fallback_ops_created");
            }
            return overrides;
        }
        if ((raw.ops() == null ? 0 : raw.ops().size()) != (sanitized.ops() == null ? 0 : sanitized.ops().size())) {
            overrides.add("ops_count_changed");
        }
        boolean createInserted = (raw.ops() == null || raw.ops().stream().noneMatch(op -> op.op() == PatchOpType.CREATE_NOTE))
            && sanitized.ops() != null
            && sanitized.ops().stream().anyMatch(op -> op.op() == PatchOpType.CREATE_NOTE);
        if (createInserted) {
            overrides.add("create_op_inserted");
        }
        boolean summaryRepaired = (raw.ops() == null || raw.ops().stream().noneMatch(op -> "summary".equals(op.sectionId())))
            && sanitized.ops() != null
            && sanitized.ops().stream().anyMatch(op -> "summary".equals(op.sectionId()));
        if (summaryRepaired) {
            overrides.add("visible_summary_repaired");
        }
        for (int index = 0; index < Math.min(raw.ops().size(), sanitized.ops().size()); index++) {
            PatchOperation rawOp = raw.ops().get(index);
            PatchOperation sanitizedOp = sanitized.ops().get(index);
            if (!java.util.Objects.equals(rawOp.sectionId(), sanitizedOp.sectionId())) {
                overrides.add("section_retargeted");
                break;
            }
            if (!java.util.Objects.equals(rawOp.contentMarkdown(), sanitizedOp.contentMarkdown())) {
                overrides.add("content_normalized");
                break;
            }
        }
        return overrides;
    }

    private <T> T readStructuredValue(String outputText, TypeReference<T> typeReference) throws IOException {
        IOException firstFailure = null;
        for (String candidate : parseCandidates(outputText)) {
            try {
                return objectMapper.readValue(candidate, typeReference);
            } catch (IOException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
            }
        }

        if (firstFailure != null) {
            log.warn("Failed to parse OpenAI structured output. excerpt={}", trimToLength(outputText, 600), firstFailure);
            throw firstFailure;
        }
        throw new IOException("OpenAI structured output did not contain a parseable JSON object.");
    }

    private List<String> parseCandidates(String outputText) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        String trimmed = outputText == null ? null : outputText.trim();
        if (trimmed != null && !trimmed.isBlank()) {
            candidates.add(trimmed);
        }

        String withoutCodeFence = stripCodeFence(trimmed);
        if (withoutCodeFence != null && !withoutCodeFence.isBlank()) {
            candidates.add(withoutCodeFence);
        }

        String extractedJson = extractJsonObject(withoutCodeFence == null ? trimmed : withoutCodeFence);
        if (extractedJson != null && !extractedJson.isBlank()) {
            candidates.add(extractedJson);
        }

        return new ArrayList<>(candidates);
    }

    private String stripCodeFence(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (!normalized.startsWith("```")) {
            return normalized;
        }
        normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
        normalized = normalized.replaceFirst("\\s*```$", "");
        return normalized.trim();
    }

    private String extractJsonObject(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace < 0 || lastBrace <= firstBrace) {
            return null;
        }
        return value.substring(firstBrace, lastBrace + 1).trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize OpenAI request body.", e);
        }
    }

    private String normalizeBaseUrl() {
        String value = aiProperties.getOpenAiBaseUrl();
        if (value == null || value.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private int maxOutputTokens(String schemaName) {
        return switch (schemaName) {
            case "noteszero_nano_triage_v1" -> 80;
            case "noteszero_route_plan_v1" -> 450;
            case "noteszero_note_summary_v1" -> 220;
            default -> 1200;
        };
    }

    private record StructuredInvocationResult<T>(
        T value,
        AiUsageTraceV1 usage,
        String responseId,
        long latencyMs
    ) {
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String trimMarkdown(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace("\r", "").trim();
        if (normalized.isBlank()) {
            return null;
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private ObjectNode routeSchema() {
        ObjectNode schema = baseObjectSchema();
        schema.set("properties", objectProperties(Map.of(
            "intent", enumSchema("string", List.of("WRITE_EXISTING_NOTE", "CREATE_NOTE", "ANSWER_ONLY", "CLARIFY", "NEED_MORE_CONTEXT")),
            "targetNotebookId", nullableIntegerSchema(),
            "targetNoteId", nullableIntegerSchema(),
            "targetNoteType", enumSchema("string", List.of(
                CanonicalNoteTemplates.PROJECT_NOTE,
                CanonicalNoteTemplates.GENERIC_NOTE,
                CanonicalNoteTemplates.ENTITY_LOG,
                CanonicalNoteTemplates.REFERENCE_NOTE
            )),
            "confidence", numberSchema(),
            "reasonCodes", stringArraySchema(),
            "strategy", enumSchema("string", List.of("DIRECT_APPLY", "NOTE_INBOX", "NOTEBOOK_INBOX", "ANSWER_ONLY", "CLARIFY")),
            "answer", nullableStringSchema(),
            "needContextNoteIds", integerArraySchema()
        )));
        schema.set("required", stringArrayNode(List.of(
            "intent",
            "targetNotebookId",
            "targetNoteId",
            "targetNoteType",
            "confidence",
            "reasonCodes",
            "strategy",
            "answer",
            "needContextNoteIds"
        )));
        return schema;
    }

    private ObjectNode patchSchema() {
        ObjectNode noteSectionSchema = baseObjectSchema();
        noteSectionSchema.set("properties", objectProperties(Map.of(
            "id", stringSchema(),
            "label", stringSchema(),
            "kind", stringSchema(),
            "contentMarkdown", nullableStringSchema()
        )));
        noteSectionSchema.set("required", stringArrayNode(List.of("id", "label", "kind", "contentMarkdown")));

        ObjectNode patchOperationSchema = baseObjectSchema();
        patchOperationSchema.set("properties", objectProperties(Map.of(
            "op", enumSchema("string", List.of(
                "CREATE_NOTE",
                "RENAME_NOTE",
                "UPDATE_NOTE_SUMMARY",
                "INSERT_SECTION_AFTER",
                "REPLACE_SECTION_CONTENT",
                "APPEND_SECTION_CONTENT",
                "DELETE_SECTION",
                "REPLACE_NOTE_OUTLINE"
            )),
            "sectionId", nullableStringSchema(),
            "afterSectionId", nullableStringSchema(),
            "title", nullableStringSchema(),
            "summaryShort", nullableStringSchema(),
            "contentMarkdown", nullableStringSchema(),
            "sections", nullableArraySchema(noteSectionSchema)
        )));
        patchOperationSchema.set("required", stringArrayNode(List.of(
            "op",
            "sectionId",
            "afterSectionId",
            "title",
            "summaryShort",
            "contentMarkdown",
            "sections"
        )));

        ObjectNode schema = baseObjectSchema();
        schema.set("properties", objectProperties(Map.of(
            "targetNotebookId", nullableIntegerSchema(),
            "targetNoteId", nullableIntegerSchema(),
            "targetNoteType", enumSchema("string", List.of(CanonicalNoteTemplates.PROJECT_NOTE, CanonicalNoteTemplates.GENERIC_NOTE)),
            "ops", arraySchema(patchOperationSchema),
            "fallbackToInbox", booleanSchema(),
            "plannerPromptVersion", stringSchema()
        )));
        schema.set("required", stringArrayNode(List.of(
            "targetNotebookId",
            "targetNoteId",
            "targetNoteType",
            "ops",
            "fallbackToInbox",
            "plannerPromptVersion"
        )));
        return schema;
    }

    private ObjectNode triageSchema() {
        ObjectNode schema = baseObjectSchema();
        schema.set("properties", objectProperties(Map.of(
            "type", enumSchema("string", List.of("write", "question", "cancel", "chitchat")),
            "reply", stringSchema()
        )));
        schema.set("required", stringArrayNode(List.of("type", "reply")));
        return schema;
    }

    private ObjectNode summarySchema() {
        ObjectNode schema = baseObjectSchema();
        schema.set("properties", objectProperties(Map.of(
            "summaryShort", stringSchema(),
            "routingSummary", stringSchema()
        )));
        schema.set("required", stringArrayNode(List.of("summaryShort", "routingSummary")));
        return schema;
    }

    private ObjectNode baseObjectSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        return schema;
    }

    private ObjectNode objectProperties(Map<String, JsonNode> properties) {
        ObjectNode node = objectMapper.createObjectNode();
        for (Map.Entry<String, JsonNode> entry : properties.entrySet()) {
            node.set(entry.getKey(), entry.getValue());
        }
        return node;
    }

    private ObjectNode stringSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "string");
        return node;
    }

    private ObjectNode nullableStringSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode type = node.putArray("type");
        type.add("string");
        type.add("null");
        return node;
    }

    private ObjectNode nullableIntegerSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode type = node.putArray("type");
        type.add("integer");
        type.add("null");
        return node;
    }

    private ObjectNode numberSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "number");
        node.put("minimum", 0.0);
        node.put("maximum", 1.0);
        return node;
    }

    private ObjectNode booleanSchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "boolean");
        return node;
    }

    private ObjectNode stringArraySchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", stringSchema());
        return node;
    }

    private ObjectNode integerArraySchema() {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        ObjectNode itemNode = objectMapper.createObjectNode();
        itemNode.put("type", "integer");
        node.set("items", itemNode);
        return node;
    }

    private ObjectNode arraySchema(JsonNode itemSchema) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "array");
        node.set("items", itemSchema);
        return node;
    }

    private ObjectNode nullableArraySchema(JsonNode itemSchema) {
        ObjectNode node = objectMapper.createObjectNode();
        ArrayNode type = node.putArray("type");
        type.add("array");
        type.add("null");
        node.set("items", itemSchema);
        return node;
    }

    private ObjectNode enumSchema(String typeName, List<String> values) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", typeName);
        ArrayNode enumValues = node.putArray("enum");
        for (String value : values) {
            enumValues.add(value);
        }
        return node;
    }

    private ArrayNode stringArrayNode(List<String> values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
