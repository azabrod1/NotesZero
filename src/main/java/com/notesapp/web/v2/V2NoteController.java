package com.notesapp.web.v2;

import com.notesapp.service.aiwrite.AiWriteProviderSelector;
import com.notesapp.service.v2.NoteWorkflowService;
import com.notesapp.web.dto.v2.NoteSummaryV2Response;
import com.notesapp.web.dto.v2.NoteV2Response;
import com.notesapp.web.dto.v2.RevisionHistoryEntryResponse;
import com.notesapp.web.dto.v2.UndoResultResponse;
import com.notesapp.web.dto.v2.UpsertNoteV2Request;
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
@RequestMapping("/api/v2/notes")
public class V2NoteController {

    private final NoteWorkflowService noteWorkflowService;
    private final AiWriteProviderSelector aiWriteProviderSelector;

    public V2NoteController(NoteWorkflowService noteWorkflowService,
                            AiWriteProviderSelector aiWriteProviderSelector) {
        this.noteWorkflowService = noteWorkflowService;
        this.aiWriteProviderSelector = aiWriteProviderSelector;
    }

    @GetMapping
    public List<NoteSummaryV2Response> listNotes(@RequestParam Long notebookId) {
        return noteWorkflowService.listNotes(notebookId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteV2Response createNote(@Valid @RequestBody UpsertNoteV2Request request) {
        return noteWorkflowService.createManual(request);
    }

    @GetMapping("/{noteId}")
    public NoteV2Response getNote(@PathVariable Long noteId) {
        return noteWorkflowService.getNote(noteId);
    }

    @PutMapping("/{noteId}")
    public NoteV2Response updateNote(@PathVariable Long noteId, @Valid @RequestBody UpsertNoteV2Request request) {
        return noteWorkflowService.updateManual(noteId, request);
    }

    @GetMapping("/{noteId}/history")
    public List<RevisionHistoryEntryResponse> history(@PathVariable Long noteId) {
        return noteWorkflowService.history(noteId);
    }

    @PostMapping("/{noteId}/undo")
    public UndoResultResponse undo(@PathVariable Long noteId, @RequestParam Long operationId) {
        return noteWorkflowService.undo(noteId, operationId);
    }

    @PostMapping("/{noteId}/recompute-summary")
    public NoteV2Response recomputeSummary(@PathVariable Long noteId) {
        return noteWorkflowService.recomputeSummary(noteId, aiWriteProviderSelector.activeProvider());
    }
}
