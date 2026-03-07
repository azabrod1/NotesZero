package com.notesapp.web;

import com.notesapp.service.NoteService;
import com.notesapp.web.dto.CreateNoteRequest;
import com.notesapp.web.dto.NoteResponse;
import com.notesapp.web.dto.UpdateNoteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse createNote(@Valid @RequestBody CreateNoteRequest request) {
        return noteService.createNote(request);
    }

    @GetMapping("/{noteId}")
    public NoteResponse getNote(@PathVariable Long noteId) {
        return noteService.getNote(noteId);
    }

    @PutMapping("/{noteId}")
    public NoteResponse updateNote(@PathVariable Long noteId, @Valid @RequestBody UpdateNoteRequest request) {
        return noteService.updateNote(noteId, request);
    }

    @GetMapping
    public List<NoteResponse> listNotes(@RequestParam Long notebookId) {
        return noteService.listNotes(notebookId);
    }
}

