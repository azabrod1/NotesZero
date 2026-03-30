package com.notesapp.web.v2;

import com.notesapp.service.NotebookService;
import com.notesapp.web.dto.CreateNotebookRequest;
import com.notesapp.web.dto.v2.NotebookV2Response;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v2/notebooks")
public class V2NotebookController {

    private final NotebookService notebookService;

    public V2NotebookController(NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @GetMapping
    public List<NotebookV2Response> listNotebooks() {
        return notebookService.listNotebookEntities().stream()
            .map(notebook -> new NotebookV2Response(
                notebook.getId(),
                notebook.getName(),
                notebook.getDescription(),
                notebook.getRoutingSummary(),
                notebook.getCreatedAt()
            ))
            .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotebookV2Response createNotebook(@Valid @RequestBody CreateNotebookRequest request) {
        var created = notebookService.createNotebook(request);
        var notebook = notebookService.getNotebookRequired(created.getId());
        return new NotebookV2Response(
            notebook.getId(),
            notebook.getName(),
            notebook.getDescription(),
            notebook.getRoutingSummary(),
            notebook.getCreatedAt()
        );
    }
}
