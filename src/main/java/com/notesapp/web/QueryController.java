package com.notesapp.web;

import com.notesapp.service.query.ChartService;
import com.notesapp.service.query.NotebookQueryService;
import com.notesapp.web.dto.ChartSeriesResponse;
import com.notesapp.web.dto.QueryRequest;
import com.notesapp.web.dto.QueryResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1")
public class QueryController {

    private final NotebookQueryService notebookQueryService;
    private final ChartService chartService;

    public QueryController(NotebookQueryService notebookQueryService, ChartService chartService) {
        this.notebookQueryService = notebookQueryService;
        this.chartService = chartService;
    }

    @PostMapping("/query")
    public QueryResponse askQuestion(@Valid @RequestBody QueryRequest request) {
        return notebookQueryService.ask(request.getNotebookId(), request.getQuestion());
    }

    @GetMapping("/charts/series")
    public ChartSeriesResponse getSeries(@RequestParam Long notebookId, @RequestParam String keyName) {
        return chartService.numericSeries(notebookId, keyName);
    }
}

