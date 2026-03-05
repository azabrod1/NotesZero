package com.notesapp.service.query;

import com.notesapp.domain.Fact;
import com.notesapp.domain.enums.FactValueType;
import com.notesapp.repository.FactRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.web.dto.ChartPointResponse;
import com.notesapp.web.dto.ChartSeriesResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChartService {

    private final FactRepository factRepository;
    private final NotebookService notebookService;

    public ChartService(FactRepository factRepository, NotebookService notebookService) {
        this.factRepository = factRepository;
        this.notebookService = notebookService;
    }

    @Transactional(readOnly = true)
    public ChartSeriesResponse numericSeries(Long notebookId, String keyName) {
        notebookService.getNotebookRequired(notebookId);
        List<Fact> facts = factRepository.findByNotebookIdAndKeyNameOrderByCreatedAtAsc(notebookId, keyName)
            .stream()
            .filter(fact -> fact.getValueType() == FactValueType.NUMBER)
            .collect(Collectors.toList());

        List<ChartPointResponse> points = facts.stream()
            .map(fact -> new ChartPointResponse(resolvePointTime(fact), fact.getValueNumber()))
            .collect(Collectors.toList());
        return new ChartSeriesResponse(keyName, points);
    }

    private Instant resolvePointTime(Fact fact) {
        if (fact.getValueDatetime() != null) {
            return fact.getValueDatetime();
        }
        if (fact.getNote().getOccurredAt() != null) {
            return fact.getNote().getOccurredAt();
        }
        return fact.getCreatedAt();
    }
}
