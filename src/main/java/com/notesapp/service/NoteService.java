package com.notesapp.service;

import com.notesapp.config.AiProperties;
import com.notesapp.domain.Attachment;
import com.notesapp.domain.ClarificationTask;
import com.notesapp.domain.Fact;
import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.RoutingFeedback;
import com.notesapp.domain.enums.ClarificationStatus;
import com.notesapp.domain.enums.ClarificationType;
import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.repository.AttachmentRepository;
import com.notesapp.repository.ClarificationTaskRepository;
import com.notesapp.repository.FactRepository;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.RoutingFeedbackRepository;
import com.notesapp.service.ai.AiProviderClient;
import com.notesapp.service.ai.AiProviderSelector;
import com.notesapp.service.extraction.ExtractedFact;
import com.notesapp.service.routing.RoutingDecision;
import com.notesapp.service.routing.RoutingInput;
import com.notesapp.service.routing.RoutingOption;
import com.notesapp.web.dto.ClarificationTaskResponse;
import com.notesapp.web.dto.CreateNoteRequest;
import com.notesapp.web.dto.NoteResponse;
import com.notesapp.web.dto.UpdateNoteRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final FactRepository factRepository;
    private final ClarificationTaskRepository clarificationTaskRepository;
    private final RoutingFeedbackRepository routingFeedbackRepository;
    private final AttachmentRepository attachmentRepository;
    private final NotebookService notebookService;
    private final ClarificationOptionCodec optionCodec;
    private final AiProviderSelector aiProviderSelector;
    private final AiProperties aiProperties;
    private final NoteResponseAssembler noteResponseAssembler;
    private final PageService pageService;

    public NoteService(NoteRepository noteRepository,
                       FactRepository factRepository,
                       ClarificationTaskRepository clarificationTaskRepository,
                       RoutingFeedbackRepository routingFeedbackRepository,
                       AttachmentRepository attachmentRepository,
                       NotebookService notebookService,
                       ClarificationOptionCodec optionCodec,
                       AiProviderSelector aiProviderSelector,
                       AiProperties aiProperties,
                       NoteResponseAssembler noteResponseAssembler,
                       PageService pageService) {
        this.noteRepository = noteRepository;
        this.factRepository = factRepository;
        this.clarificationTaskRepository = clarificationTaskRepository;
        this.routingFeedbackRepository = routingFeedbackRepository;
        this.attachmentRepository = attachmentRepository;
        this.notebookService = notebookService;
        this.optionCodec = optionCodec;
        this.aiProviderSelector = aiProviderSelector;
        this.aiProperties = aiProperties;
        this.noteResponseAssembler = noteResponseAssembler;
        this.pageService = pageService;
    }

    @Transactional
    public NoteResponse createNote(CreateNoteRequest request) {
        AiProviderClient aiClient = aiProviderSelector.activeClient();
        Instant now = Instant.now();
        Notebook notebook = null;
        RoutingDecision routingDecision = RoutingDecision.unresolved(new ArrayList<>());
        List<Notebook> notebookEntities = notebookService.listNotebookEntities();

        if (request.getNotebookId() != null) {
            notebook = notebookService.getNotebookRequired(request.getNotebookId());
        } else {
            List<RoutingFeedback> feedback = routingFeedbackRepository.findTop100ByOrderByCreatedAtDesc();
            routingDecision = aiClient.routeNotebook(new RoutingInput(request.getRawText(), notebookEntities, feedback));
            if (routingDecision.getNotebookId().isPresent() &&
                routingDecision.getConfidence() >= aiProperties.getNotebookRoutingHighRiskThreshold()) {
                notebook = notebookService.getNotebookRequired(routingDecision.getNotebookId().get());
            }
        }

        Optional<Note> duplicate = findExactDuplicate(request, notebook);
        if (duplicate.isPresent()) {
            Note existing = duplicate.get();
            List<Fact> facts = factRepository.findByNote_Id(existing.getId());
            List<ClarificationTask> tasks = clarificationTaskRepository.findByNote_IdAndStatus(existing.getId(), ClarificationStatus.OPEN);
            return noteResponseAssembler.toResponse(existing, facts, tasks);
        }

        Note note = new Note(
            NotebookService.DEFAULT_USER_ID,
            notebook,
            request.getRawText(),
            request.getSourceType(),
            NoteStatus.READY,
            request.getOccurredAt(),
            now,
            now
        );
        Note savedNote = noteRepository.save(note);

        List<ClarificationTask> tasks = new ArrayList<>();
        if (notebook == null) {
            List<String> options = buildNotebookOptions(routingDecision, notebookEntities);
            ClarificationTask notebookTask = new ClarificationTask(
                savedNote,
                ClarificationType.NOTEBOOK_ASSIGNMENT,
                "Which notebook should this note go to?",
                optionCodec.encode(options),
                ClarificationStatus.OPEN,
                now
            );
            tasks.add(clarificationTaskRepository.save(notebookTask));
        }

        List<ExtractedFact> extractedFacts = aiClient.extractFacts(request.getRawText());
        List<Fact> persistedFacts = persistFacts(savedNote, extractedFacts, now);
        tasks.addAll(createHighRiskFactTasks(savedNote, extractedFacts, now));

        if (request.getPhotoFileName() != null && !request.getPhotoFileName().isBlank()) {
            attachmentRepository.save(new Attachment(
                savedNote,
                request.getPhotoFileName(),
                request.getPhotoContentType() == null ? "application/octet-stream" : request.getPhotoContentType(),
                "local://" + request.getPhotoFileName(),
                now
            ));
        }

        if (!tasks.isEmpty()) {
            savedNote.setStatus(NoteStatus.NEEDS_CLARIFICATION);
            savedNote.setUpdatedAt(now);
            savedNote = noteRepository.save(savedNote);
        } else if (savedNote.getNotebook() != null) {
            pageService.appendNoteToNotebookPage(savedNote.getNotebook(), savedNote);
        }

        return noteResponseAssembler.toResponse(savedNote, persistedFacts, tasks);
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listNotes(Long notebookId) {
        Notebook notebook = notebookService.getNotebookRequired(notebookId);
        return noteRepository.findByUserIdAndNotebook_IdOrderByCreatedAtDesc(NotebookService.DEFAULT_USER_ID, notebook.getId())
            .stream()
            .map(note -> {
                List<Fact> facts = factRepository.findByNote_Id(note.getId());
                List<ClarificationTask> tasks = clarificationTaskRepository.findByNote_IdAndStatus(note.getId(), ClarificationStatus.OPEN);
                return noteResponseAssembler.toResponse(note, facts, tasks);
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public NoteResponse getNote(Long noteId) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
        List<Fact> facts = factRepository.findByNote_Id(note.getId());
        List<ClarificationTask> tasks = clarificationTaskRepository.findByNote_IdAndStatus(note.getId(), ClarificationStatus.OPEN);
        return noteResponseAssembler.toResponse(note, facts, tasks);
    }

    @Transactional
    public NoteResponse updateNote(Long noteId, UpdateNoteRequest request) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));

        if (request.getNotebookId() != null) {
            Notebook notebook = notebookService.getNotebookRequired(request.getNotebookId());
            note.setNotebook(notebook);
        }

        note.setRawText(request.getRawText().trim());
        note.setOccurredAt(request.getOccurredAt());
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);

        factRepository.deleteByNote_Id(note.getId());
        clarificationTaskRepository.deleteByNote_IdAndStatus(note.getId(), ClarificationStatus.OPEN);

        AiProviderClient aiClient = aiProviderSelector.activeClient();
        Instant now = Instant.now();
        List<ExtractedFact> extractedFacts = aiClient.extractFacts(note.getRawText());
        List<Fact> persistedFacts = persistFacts(note, extractedFacts, now);
        List<ClarificationTask> tasks = createHighRiskFactTasks(note, extractedFacts, now);

        if (!tasks.isEmpty()) {
            note.setStatus(NoteStatus.NEEDS_CLARIFICATION);
        } else {
            note.setStatus(NoteStatus.READY);
            if (note.getNotebook() != null) {
                pageService.appendNoteToNotebookPage(note.getNotebook(), note);
            }
        }
        note.setUpdatedAt(now);
        noteRepository.save(note);
        return noteResponseAssembler.toResponse(note, persistedFacts, tasks);
    }

    @Transactional
    public NoteResponse resolveClarification(Long taskId, String selectedOption) {
        ClarificationTask task = clarificationTaskRepository.findById(taskId)
            .orElseThrow(() -> new NotFoundException("Clarification task not found: " + taskId));

        if (task.getStatus() == ClarificationStatus.RESOLVED) {
            throw new ValidationException("Task already resolved: " + taskId);
        }
        List<String> options = optionCodec.decode(task.getOptionsJson());
        if (!options.contains(selectedOption)) {
            throw new ValidationException("Invalid option: " + selectedOption);
        }

        Note note = task.getNote();
        Instant now = Instant.now();
        if (task.getType() == ClarificationType.NOTEBOOK_ASSIGNMENT) {
            Notebook notebook = notebookService.listNotebookEntities().stream()
                .filter(item -> item.getName().equalsIgnoreCase(selectedOption))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Notebook option no longer exists: " + selectedOption));
            note.setNotebook(notebook);
            routingFeedbackRepository.save(new RoutingFeedback(
                summarizePhrase(note.getRawText()),
                notebook,
                now
            ));
            List<Fact> facts = factRepository.findByNote_Id(note.getId());
            for (Fact fact : facts) {
                fact.setNotebookId(notebook.getId());
                factRepository.save(fact);
            }
            note.setUpdatedAt(now);
            noteRepository.save(note);
        } else if (task.getType() == ClarificationType.TEMPERATURE_UNIT) {
            List<Fact> facts = factRepository.findByNote_Id(note.getId());
            for (Fact fact : facts) {
                if ("body_temperature".equals(fact.getKeyName()) && fact.getUnit() == null) {
                    fact.setUnit(selectedOption.toUpperCase(Locale.ROOT));
                    factRepository.save(fact);
                }
            }
        }

        task.setStatus(ClarificationStatus.RESOLVED);
        task.setResolvedOption(selectedOption);
        task.setResolvedAt(now);
        clarificationTaskRepository.save(task);

        List<ClarificationTask> remaining = clarificationTaskRepository.findByNote_IdAndStatus(note.getId(), ClarificationStatus.OPEN);
        if (remaining.isEmpty()) {
            note.setStatus(NoteStatus.READY);
            note.setUpdatedAt(now);
            noteRepository.save(note);
            if (note.getNotebook() != null) {
                pageService.appendNoteToNotebookPage(note.getNotebook(), note);
            }
        }
        return getNote(note.getId());
    }

    @Transactional(readOnly = true)
    public List<ClarificationTaskResponse> listOpenClarifications() {
        return clarificationTaskRepository.findByStatusOrderByCreatedAtAsc(ClarificationStatus.OPEN)
            .stream()
            .map(task -> new ClarificationTaskResponse(
                task.getId(),
                task.getNote().getId(),
                task.getType(),
                task.getQuestion(),
                optionCodec.decode(task.getOptionsJson()),
                task.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }

    private Optional<Note> findExactDuplicate(CreateNoteRequest request, Notebook notebook) {
        if (request.getOccurredAt() == null || notebook == null) {
            return Optional.empty();
        }
        return noteRepository.findByUserIdAndNotebook_IdAndOccurredAtAndRawText(
            NotebookService.DEFAULT_USER_ID,
            notebook.getId(),
            request.getOccurredAt(),
            request.getRawText()
        );
    }

    private List<String> buildNotebookOptions(RoutingDecision decision, List<Notebook> notebooks) {
        List<String> options = decision.getOptions().stream()
            .map(RoutingOption::getNotebookName)
            .collect(Collectors.toList());
        if (options.isEmpty()) {
            options = notebooks.stream()
                .limit(3)
                .map(Notebook::getName)
                .collect(Collectors.toList());
        }
        return options;
    }

    private List<Fact> persistFacts(Note note, List<ExtractedFact> extractedFacts, Instant now) {
        List<Fact> persisted = new ArrayList<>();
        Long notebookId = note.getNotebook() != null ? note.getNotebook().getId() : -1L;

        for (ExtractedFact extractedFact : extractedFacts) {
            Fact fact = new Fact(
                note,
                notebookId,
                extractedFact.getKeyName(),
                extractedFact.getValueType(),
                extractedFact.getNumberValue(),
                extractedFact.getTextValue(),
                extractedFact.getDatetimeValue(),
                extractedFact.getUnit(),
                extractedFact.getConfidence(),
                now
            );
            persisted.add(factRepository.save(fact));
        }
        return persisted;
    }

    private List<ClarificationTask> createHighRiskFactTasks(Note note, List<ExtractedFact> extractedFacts, Instant now) {
        List<ClarificationTask> tasks = new ArrayList<>();
        for (ExtractedFact extractedFact : extractedFacts) {
            if (!extractedFact.isHighRisk()) {
                continue;
            }
            ClarificationTask task = new ClarificationTask(
                note,
                ClarificationType.TEMPERATURE_UNIT,
                extractedFact.getClarificationQuestion(),
                optionCodec.encode(List.of("F", "C")),
                ClarificationStatus.OPEN,
                now
            );
            tasks.add(clarificationTaskRepository.save(task));
        }
        return tasks;
    }

    private String summarizePhrase(String rawText) {
        if (rawText == null) {
            return "";
        }
        String text = rawText.trim();
        if (text.length() <= 80) {
            return text.toLowerCase(Locale.ROOT);
        }
        return text.substring(0, 80).toLowerCase(Locale.ROOT);
    }
}
