package com.notesapp.service.query;

import com.notesapp.domain.Fact;
import com.notesapp.domain.Note;
import com.notesapp.domain.enums.FactValueType;
import com.notesapp.repository.FactRepository;
import com.notesapp.repository.NoteRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.web.dto.QueryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class NotebookQueryService {

    private final NoteRepository noteRepository;
    private final FactRepository factRepository;
    private final NotebookService notebookService;

    public NotebookQueryService(NoteRepository noteRepository, FactRepository factRepository, NotebookService notebookService) {
        this.noteRepository = noteRepository;
        this.factRepository = factRepository;
        this.notebookService = notebookService;
    }

    @Transactional(readOnly = true)
    public QueryResponse ask(Long notebookId, String question) {
        notebookService.getNotebookRequired(notebookId);
        String normalized = question.toLowerCase(Locale.ROOT);

        if (normalized.contains("fever") || normalized.contains("temperature")) {
            return answerTemperatureQuestion(notebookId);
        }
        if (normalized.contains("how many")) {
            return answerCountQuestion(notebookId, normalized);
        }
        return answerFromRecentNotes(notebookId);
    }

    private QueryResponse answerTemperatureQuestion(Long notebookId) {
        List<Fact> facts = factRepository.findByNotebookIdAndKeyNameOrderByCreatedAtAsc(notebookId, "body_temperature")
            .stream()
            .filter(fact -> fact.getValueType() == FactValueType.NUMBER)
            .collect(Collectors.toList());
        if (facts.isEmpty()) {
            return new QueryResponse("No temperature entries found yet.", List.of());
        }
        Fact maxFact = facts.stream()
            .max(Comparator.comparing(Fact::getValueNumber))
            .orElse(facts.get(0));
        BigDecimal average = facts.stream()
            .map(Fact::getValueNumber)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(facts.size()), 2, RoundingMode.HALF_UP);
        String unit = maxFact.getUnit() == null ? "" : " " + maxFact.getUnit();
        String answer = "Max temperature is " + maxFact.getValueNumber().stripTrailingZeros().toPlainString() + unit
            + ". Average is " + average.stripTrailingZeros().toPlainString() + unit + ".";
        List<Long> citations = facts.stream()
            .map(fact -> fact.getNote().getId())
            .distinct()
            .collect(Collectors.toList());
        return new QueryResponse(answer, citations);
    }

    private QueryResponse answerCountQuestion(Long notebookId, String question) {
        String target = extractTargetToken(question);
        List<Fact> facts = factRepository.findTop100ByNotebookIdOrderByCreatedAtDesc(notebookId)
            .stream()
            .filter(fact -> fact.getValueType() == FactValueType.NUMBER)
            .collect(Collectors.toList());
        if (!target.isBlank()) {
            facts = facts.stream()
                .filter(fact -> fact.getKeyName().contains(target))
                .collect(Collectors.toList());
        }
        if (facts.isEmpty()) {
            return new QueryResponse("I could not find matching numeric facts yet.", List.of());
        }
        BigDecimal latest = facts.get(0).getValueNumber();
        String key = facts.get(0).getKeyName();
        String answer = "Latest value for " + key + " is " + latest.stripTrailingZeros().toPlainString() + ".";
        List<Long> citations = facts.stream().limit(5).map(f -> f.getNote().getId()).distinct().collect(Collectors.toList());
        return new QueryResponse(answer, citations);
    }

    private QueryResponse answerFromRecentNotes(Long notebookId) {
        List<Note> notes = noteRepository.findTop20ByUserIdAndNotebook_IdOrderByCreatedAtDesc(NotebookService.DEFAULT_USER_ID, notebookId);
        if (notes.isEmpty()) {
            return new QueryResponse("No notes found in this notebook yet.", List.of());
        }
        String joined = notes.stream()
            .limit(3)
            .map(Note::getRawText)
            .collect(Collectors.joining(" | "));
        List<Long> citations = notes.stream()
            .limit(3)
            .map(Note::getId)
            .collect(Collectors.toCollection(ArrayList::new));
        return new QueryResponse("Recent notes: " + joined, citations);
    }

    private String extractTargetToken(String question) {
        String[] tokens = question.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() > 3 && !List.of("many", "what", "have", "with").contains(token)) {
                return token;
            }
        }
        return "";
    }
}
