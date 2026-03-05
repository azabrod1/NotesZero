package com.notesapp.web;

import com.notesapp.service.NotebookService;
import com.notesapp.web.dto.CreateNotebookRequest;
import com.notesapp.web.dto.NotebookResponse;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/notebooks")
public class NotebookController {

    private final NotebookService notebookService;

    public NotebookController(NotebookService notebookService) {
        this.notebookService = notebookService;
    }

    @GetMapping
    public List<NotebookResponse> listNotebooks() {
        return notebookService.listNotebooks();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotebookResponse createNotebook(@Valid @RequestBody CreateNotebookRequest request) {
        return notebookService.createNotebook(request);
    }
}
