package com.notesapp.web;

import com.notesapp.service.NoteService;
import com.notesapp.web.dto.ClarificationTaskResponse;
import com.notesapp.web.dto.NoteResponse;
import com.notesapp.web.dto.ResolveClarificationRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/clarifications")
public class ClarificationController {

    private final NoteService noteService;

    public ClarificationController(NoteService noteService) {
        this.noteService = noteService;
    }

    @GetMapping
    public List<ClarificationTaskResponse> listOpenTasks() {
        return noteService.listOpenClarifications();
    }

    @PostMapping("/{taskId}/resolve")
    public NoteResponse resolveTask(@PathVariable Long taskId, @Valid @RequestBody ResolveClarificationRequest request) {
        return noteService.resolveClarification(taskId, request.getSelectedOption());
    }
}

