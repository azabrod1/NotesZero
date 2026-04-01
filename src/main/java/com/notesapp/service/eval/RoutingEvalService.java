package com.notesapp.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.service.aiwrite.AiWriteProvider;
import com.notesapp.service.aiwrite.AiWriteProviderSelector;
import com.notesapp.service.aiwrite.DeterministicRetrievalService;
import com.notesapp.service.aiwrite.RetrievalBundle;
import com.notesapp.service.aiwrite.RouteDecision;
import com.notesapp.service.aiwrite.RouteIntent;
import com.notesapp.service.aiwrite.RouteRequestContext;
import com.notesapp.service.aiwrite.RoutePlanV1;
import com.notesapp.service.document.NoteDocumentV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RoutingEvalService {

    private static final Logger log = LoggerFactory.getLogger(RoutingEvalService.class);

    private final DeterministicRetrievalService retrievalService;
    private final AiWriteProviderSelector providerSelector;
    private final ObjectMapper objectMapper;

    public RoutingEvalService(DeterministicRetrievalService retrievalService,
                              AiWriteProviderSelector providerSelector,
                              ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.providerSelector = providerSelector;
        this.objectMapper = objectMapper;
    }

    public List<GoldSetCase> loadGoldSet() throws IOException {
        ObjectMapper reader = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        try (InputStream is = new ClassPathResource("eval/gold-set.json").getInputStream()) {
            return reader.readValue(is, new TypeReference<>() {});
        }
    }

    @Transactional(readOnly = true)
    public EvalReport runEval() throws IOException {
        List<GoldSetCase> cases = loadGoldSet();
        AiWriteProvider provider = providerSelector.activeProvider();
        List<EvalResult> results = new ArrayList<>();

        for (GoldSetCase testCase : cases) {
            try {
                EvalResult result = evaluateCase(testCase, provider);
                results.add(result);
                log.info("Eval [{}]: intent={} notebook={} note={} wrongTarget={}",
                    testCase.id(),
                    result.intentCorrect() ? "OK" : "FAIL",
                    result.notebookCorrect() ? "OK" : "SKIP",
                    result.noteCorrect() ? "OK" : "SKIP",
                    result.wrongTarget() ? "YES" : "no");
            } catch (Exception e) {
                log.error("Eval [{}]: error - {}", testCase.id(), e.getMessage());
                results.add(errorResult(testCase, e));
            }
        }

        EvalReport report = EvalReport.fromResults(results);
        log.info("=== EVAL REPORT ===");
        log.info("Total cases: {}", report.totalCases());
        log.info("Intent accuracy: {}/{} ({}%)", report.intentCorrect(), report.totalCases(), String.format("%.1f", report.intentAccuracy() * 100));
        log.info("Notebook accuracy: {}/{}", report.notebookCorrect(), report.totalCases());
        log.info("Candidate recall@5: {}/{} ({}%)", report.candidateRecallAt5(), report.totalCases(), String.format("%.1f", report.candidateRecallAt5Rate() * 100));
        log.info("Wrong-target rate: {}/{} ({}%)", report.wrongTargetWrites(), report.totalCases(), String.format("%.1f", report.wrongTargetRate() * 100));
        return report;
    }

    private EvalResult evaluateCase(GoldSetCase testCase, AiWriteProvider provider) {
        RetrievalBundle bundle = retrievalService.retrieve(
            testCase.message(),
            testCase.selectedNotebookId(),
            testCase.selectedNoteId()
        );

        NoteDocumentV1 selectedDoc = testCase.selectedNoteId() == null
            ? null
            : retrievalService.getSelectedNoteDocument(testCase.selectedNoteId());

        RouteRequestContext routeContext = new RouteRequestContext(
            testCase.message(),
            testCase.selectedNotebookId(),
            testCase.selectedNoteId(),
            List.of(),
            bundle,
            selectedDoc
        );

        RouteDecision decision = provider.routeWithTrace(routeContext);
        RoutePlanV1 route = decision.routePlan();
        GoldSetCase.GoldSetExpected expected = testCase.expected();

        boolean intentCorrect = expected.intent() == null
            || expected.intent().equals(route.intent().name());

        boolean notebookCorrect = expected.notebookName() == null
            || notebookMatchesByName(bundle, route.targetNotebookId(), expected.notebookName());

        boolean noteCorrect = expected.noteTitle() == null
            || noteMatchesByTitle(bundle, route.targetNoteId(), expected.noteTitle());

        boolean candidateRecallAt5 = expected.noteTitle() == null
            || bundle.noteCandidates().stream()
                .limit(5)
                .anyMatch(c -> titleMatches(c.title(), expected.noteTitle()));

        boolean candidateRecallAt8 = expected.noteTitle() == null
            || bundle.noteCandidates().stream()
                .limit(8)
                .anyMatch(c -> titleMatches(c.title(), expected.noteTitle()));

        boolean notebookRecallAt3 = expected.notebookName() == null
            || bundle.notebookCandidates().stream()
                .limit(3)
                .anyMatch(c -> nameMatches(c.name(), expected.notebookName()));

        boolean wrongTarget = isWrongTarget(route, expected, bundle);

        List<String> errors = new ArrayList<>();
        if (!intentCorrect) {
            errors.add("Intent: expected " + expected.intent() + " got " + route.intent());
        }
        if (!notebookCorrect && expected.notebookName() != null) {
            errors.add("Notebook: expected " + expected.notebookName() + " got notebookId=" + route.targetNotebookId());
        }
        if (!noteCorrect && expected.noteTitle() != null) {
            errors.add("Note: expected " + expected.noteTitle() + " got noteId=" + route.targetNoteId());
        }
        if (wrongTarget) {
            errors.add("WRONG TARGET WRITE");
        }

        return new EvalResult(
            testCase.id(),
            testCase.message(),
            expected,
            route,
            bundle,
            intentCorrect,
            notebookCorrect,
            noteCorrect,
            candidateRecallAt5,
            candidateRecallAt8,
            notebookRecallAt3,
            wrongTarget,
            errors
        );
    }

    private boolean isWrongTarget(RoutePlanV1 route, GoldSetCase.GoldSetExpected expected, RetrievalBundle bundle) {
        if (route.intent() == RouteIntent.ANSWER_ONLY || route.intent() == RouteIntent.CLARIFY) {
            return false;
        }
        if (expected.intent() != null && expected.intent().equals("ANSWER_ONLY")) {
            return route.intent() == RouteIntent.WRITE_EXISTING_NOTE || route.intent() == RouteIntent.CREATE_NOTE;
        }
        if (expected.notebookName() != null && route.targetNotebookId() != null) {
            boolean nbMatch = notebookMatchesByName(bundle, route.targetNotebookId(), expected.notebookName());
            if (!nbMatch) {
                return true;
            }
        }
        if (expected.noteTitle() != null && route.targetNoteId() != null) {
            boolean noteMatch = noteMatchesByTitle(bundle, route.targetNoteId(), expected.noteTitle());
            if (!noteMatch) {
                return true;
            }
        }
        return false;
    }

    private boolean notebookMatchesByName(RetrievalBundle bundle, Long notebookId, String expectedName) {
        if (notebookId == null || expectedName == null) return false;
        return bundle.notebookCandidates().stream()
            .anyMatch(c -> c.notebookId().equals(notebookId) && nameMatches(c.name(), expectedName));
    }

    private boolean noteMatchesByTitle(RetrievalBundle bundle, Long noteId, String expectedTitle) {
        if (noteId == null || expectedTitle == null) return false;
        return bundle.noteCandidates().stream()
            .anyMatch(c -> c.noteId().equals(noteId) && titleMatches(c.title(), expectedTitle));
    }

    private boolean nameMatches(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT))
            || expected.toLowerCase(Locale.ROOT).contains(actual.toLowerCase(Locale.ROOT));
    }

    private boolean titleMatches(String actual, String expected) {
        return nameMatches(actual, expected);
    }

    private EvalResult errorResult(GoldSetCase testCase, Exception e) {
        return new EvalResult(
            testCase.id(),
            testCase.message(),
            testCase.expected(),
            null,
            null,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            List.of("ERROR: " + e.getMessage())
        );
    }
}
