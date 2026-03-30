package com.notesapp.service.aiwrite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.domain.enums.SourceType;
import com.notesapp.repository.NoteRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentCodec;
import com.notesapp.service.document.NoteDocumentMeta;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterministicRetrievalServiceTest {

    private final NoteDocumentCodec codec = new NoteDocumentCodec(new ObjectMapper());

    @Test
    void familyCaptureBeatsSelectedDogContext() throws Exception {
        Notebook dogNotebook = notebook(1L, "Dog notes", "Dog health and behavior notes");
        Notebook familyNotebook = notebook(3L, "Family health", "Baby and family health observations");

        Note selectedDogNote = note(
            11L,
            dogNotebook,
            "Hi",
            "hi",
            "generic_note/v1",
            Instant.now(),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Hi", "hi", "generic_note/v1", "v1", 1L, 2L),
                List.of(new NoteSection("body", "Body", "body", "Dog content"))
            )
        );

        NotebookService notebookService = mock(NotebookService.class);
        when(notebookService.listNotebookEntities()).thenReturn(List.of(dogNotebook, familyNotebook));

        NoteRepository noteRepository = mock(NoteRepository.class);
        when(noteRepository.findTop50ByUserIdOrderByUpdatedAtDesc(NotebookService.DEFAULT_USER_ID))
            .thenReturn(List.of(selectedDogNote));

        DeterministicRetrievalService retrievalService = new DeterministicRetrievalService(
            notebookService,
            noteRepository,
            codec
        );

        RetrievalBundle bundle = retrievalService.retrieve("my toddler pooped on floor", 1L, 11L);

        assertThat(bundle.notebookCandidates()).isNotEmpty();
        assertThat(bundle.notebookCandidates().getFirst().notebookId()).isEqualTo(3L);
        assertThat(bundle.notebookCandidates().getFirst().name()).isEqualTo("Family health");
        assertThat(bundle.noteCandidates()).hasSize(1);
        assertThat(bundle.noteCandidates().getFirst().noteId()).isEqualTo(11L);
        assertThat(bundle.noteCandidates().getFirst().score()).isLessThan(0.35);
        assertThat(bundle.noteCandidates().getFirst().topSectionSnippet()).isNull();
    }

    @Test
    void explicitCurrentNoteEditKeepsSelectedNoteBoost() throws Exception {
        Notebook workNotebook = notebook(2L, "Work websites", "Work links, resources, and references");
        Note selectedWorkNote = note(
            21L,
            workNotebook,
            "Deploy Runbook",
            "Deployment and rollback steps",
            CanonicalNoteTemplates.GENERIC_NOTE,
            Instant.now(),
            new NoteDocumentV1(
                new NoteDocumentMeta(21L, "Deploy Runbook", "Deployment and rollback steps", CanonicalNoteTemplates.GENERIC_NOTE, "v1", 2L, 4L),
                List.of(new NoteSection("body", "Body", "body", "Current runbook content"))
            )
        );

        NotebookService notebookService = mock(NotebookService.class);
        when(notebookService.listNotebookEntities()).thenReturn(List.of(workNotebook));

        NoteRepository noteRepository = mock(NoteRepository.class);
        when(noteRepository.findTop50ByUserIdOrderByUpdatedAtDesc(NotebookService.DEFAULT_USER_ID))
            .thenReturn(List.of(selectedWorkNote));

        DeterministicRetrievalService retrievalService = new DeterministicRetrievalService(
            notebookService,
            noteRepository,
            codec
        );

        RetrievalBundle bundle = retrievalService.retrieve("Add a task to this note to verify rollout health checks.", 2L, 21L);

        assertThat(bundle.noteCandidates()).isNotEmpty();
        assertThat(bundle.noteCandidates().getFirst().noteId()).isEqualTo(21L);
        assertThat(bundle.noteCandidates().getFirst().score()).isGreaterThan(0.70);
        assertThat(bundle.noteCandidates().getFirst().exactTitleMatch()).isFalse();
    }

    private Notebook notebook(Long id, String name, String description) throws Exception {
        Notebook notebook = new Notebook(NotebookService.DEFAULT_USER_ID, name, description, Instant.now());
        notebook.setRoutingSummary(description);
        setId(notebook, id);
        return notebook;
    }

    private Note note(Long id,
                      Notebook notebook,
                      String title,
                      String summaryShort,
                      String noteType,
                      Instant updatedAt,
                      NoteDocumentV1 document) throws Exception {
        Note note = new Note(
            NotebookService.DEFAULT_USER_ID,
            notebook,
            "{}",
            SourceType.TEXT,
            NoteStatus.READY,
            null,
            updatedAt,
            updatedAt
        );
        setId(note, id);
        note.setTitle(title);
        note.setSummaryShort(summaryShort);
        note.setNoteType(noteType);
        note.setSchemaVersion("v1");
        note.setDocumentJson(codec.write(document));
        note.setEditorStateJson("[]");
        note.setUpdatedAt(updatedAt);
        return note;
    }

    private void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
