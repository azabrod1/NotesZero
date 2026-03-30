package com.notesapp.service.v2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.domain.ChatEvent;
import com.notesapp.domain.DocumentOperation;
import com.notesapp.domain.Note;
import com.notesapp.domain.NoteRevision;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.domain.enums.SourceType;
import com.notesapp.repository.DocumentOperationRepository;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.NoteRevisionRepository;
import com.notesapp.service.NotFoundException;
import com.notesapp.service.NotebookService;
import com.notesapp.service.ValidationException;
import com.notesapp.service.aiwrite.AiWriteProvider;
import com.notesapp.service.aiwrite.ApplyResultV1;
import com.notesapp.service.aiwrite.DiffEntry;
import com.notesapp.service.aiwrite.PatchOpType;
import com.notesapp.service.aiwrite.PatchOperation;
import com.notesapp.service.aiwrite.PatchPlanV1;
import com.notesapp.service.aiwrite.ProvenanceV1;
import com.notesapp.service.aiwrite.RouteIntent;
import com.notesapp.service.aiwrite.RoutePlanV1;
import com.notesapp.service.aiwrite.RouteStrategy;
import com.notesapp.service.document.BlockNoteMapper;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentCodec;
import com.notesapp.service.document.NoteDocumentMeta;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import com.notesapp.web.dto.v2.NoteSummaryV2Response;
import com.notesapp.web.dto.v2.NoteV2Response;
import com.notesapp.web.dto.v2.RevisionHistoryEntryResponse;
import com.notesapp.web.dto.v2.UndoResultResponse;
import com.notesapp.web.dto.v2.UpsertNoteV2Request;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NoteWorkflowService {

    private static final String SYSTEM_INBOX_TITLE = "Inbox";

    private final NoteRepository noteRepository;
    private final NoteRevisionRepository noteRevisionRepository;
    private final DocumentOperationRepository documentOperationRepository;
    private final NotebookService notebookService;
    private final CanonicalNoteTemplates canonicalNoteTemplates;
    private final NoteDocumentCodec noteDocumentCodec;
    private final BlockNoteMapper blockNoteMapper;
    private final ObjectMapper objectMapper;

    public NoteWorkflowService(NoteRepository noteRepository,
                               NoteRevisionRepository noteRevisionRepository,
                               DocumentOperationRepository documentOperationRepository,
                               NotebookService notebookService,
                               CanonicalNoteTemplates canonicalNoteTemplates,
                               NoteDocumentCodec noteDocumentCodec,
                               BlockNoteMapper blockNoteMapper,
                               ObjectMapper objectMapper) {
        this.noteRepository = noteRepository;
        this.noteRevisionRepository = noteRevisionRepository;
        this.documentOperationRepository = documentOperationRepository;
        this.notebookService = notebookService;
        this.canonicalNoteTemplates = canonicalNoteTemplates;
        this.noteDocumentCodec = noteDocumentCodec;
        this.blockNoteMapper = blockNoteMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<NoteSummaryV2Response> listNotes(Long notebookId) {
        notebookService.getNotebookRequired(notebookId);
        return noteRepository.findTop50ByUserIdAndNotebook_IdOrderByUpdatedAtDesc(NotebookService.DEFAULT_USER_ID, notebookId)
            .stream()
            .filter(this::isVisibleNote)
            .sorted(Comparator.comparing(Note::getUpdatedAt).reversed())
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public NoteV2Response getNote(Long noteId) {
        return toDetailResponse(requireNote(noteId));
    }

    @Transactional(readOnly = true)
    public NoteDocumentV1 loadDocument(Long noteId) {
        return noteDocumentCodec.read(requireNote(noteId).getDocumentJson());
    }

    @Transactional
    public NoteV2Response createManual(UpsertNoteV2Request request) {
        if (request.getNotebookId() == null) {
            throw new ValidationException("notebookId is required");
        }
        Notebook notebook = notebookService.getNotebookRequired(request.getNotebookId());
        String noteType = canonicalNoteTemplates.normalizeNoteType(request.getNoteType());
        String initialTitle = request.getTitle() == null || request.getTitle().isBlank() ? "Untitled note" : request.getTitle().trim();
        NoteDocumentV1 template = canonicalNoteTemplates.createTemplate(noteType, notebook.getId(), initialTitle);
        NoteDocumentV1 document = blockNoteMapper.fromEditorJson(
            request.getEditorContent(),
            template,
            noteType,
            notebook.getId(),
            null,
            null
        );

        Instant now = Instant.now();
        Note note = new Note(
            NotebookService.DEFAULT_USER_ID,
            notebook,
            request.getEditorContent(),
            SourceType.TEXT,
            NoteStatus.READY,
            null,
            now,
            now
        );
        populateNote(note, document, request.getEditorContent());
        Note saved = noteRepository.save(note);
        NoteRevision revision = createRevision(saved, null, now);
        saved.setCurrentRevisionId(revision.getId());
        noteRepository.save(saved);
        refreshNotebookSummary(notebook);
        return toDetailResponse(saved);
    }

    @Transactional
    public NoteV2Response updateManual(Long noteId, UpsertNoteV2Request request) {
        Note note = requireNote(noteId);
        validateExpectedRevision(note, request.getCurrentRevisionId());
        if (request.getNotebookId() != null && (note.getNotebook() == null || !request.getNotebookId().equals(note.getNotebook().getId()))) {
            note.setNotebook(notebookService.getNotebookRequired(request.getNotebookId()));
        }
        NoteDocumentV1 currentDocument = noteDocumentCodec.read(note.getDocumentJson());
        NoteDocumentV1 updatedDocument = blockNoteMapper.fromEditorJson(
            request.getEditorContent(),
            currentDocument,
            note.getNoteType(),
            note.getNotebook() == null ? null : note.getNotebook().getId(),
            note.getId(),
            note.getCurrentRevisionId()
        );
        populateNote(note, updatedDocument, request.getEditorContent());
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);
        NoteRevision revision = createRevision(note, null, note.getUpdatedAt());
        note.setCurrentRevisionId(revision.getId());
        noteRepository.save(note);
        refreshNotebookSummary(note.getNotebook());
        return toDetailResponse(note);
    }

    @Transactional
    public MutationResult applyPlannedMutation(RoutePlanV1 routePlan,
                                               PatchPlanV1 patchPlan,
                                               ChatEvent chatEvent,
                                               Long expectedRevisionId,
                                               AiWriteProvider provider) {
        Note note = resolveTargetNote(routePlan, patchPlan);
        validateExpectedRevision(note, expectedRevisionId);

        NoteRevision beforeRevision = note.getCurrentRevisionId() == null
            ? createRevision(note, chatEvent, chatEvent.getCreatedAt())
            : noteRevisionRepository.findById(note.getCurrentRevisionId())
                .orElseThrow(() -> new NotFoundException("Revision not found: " + note.getCurrentRevisionId()));
        if (note.getCurrentRevisionId() == null) {
            note.setCurrentRevisionId(beforeRevision.getId());
            noteRepository.save(note);
        }

        NoteDocumentV1 beforeDocument = noteDocumentCodec.read(note.getDocumentJson());
        List<PatchOperation> effectiveOps = patchPlan.ops();
        if (routePlan.strategy() == RouteStrategy.NOTEBOOK_INBOX && "Inbox".equalsIgnoreCase(note.getTitle())) {
            effectiveOps = patchPlan.ops().stream()
                .filter(op -> op.op() != PatchOpType.CREATE_NOTE)
                .toList();
        }
        NoteDocumentV1 afterDocument = applyOps(beforeDocument, effectiveOps, note.getId(), note.getNotebook() == null ? null : note.getNotebook().getId(), note.getCurrentRevisionId());
        String editorJson = blockNoteMapper.toEditorJson(afterDocument);
        populateNote(note, afterDocument, editorJson);
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);

        NoteRevision afterRevision = createRevision(note, chatEvent, note.getUpdatedAt());
        note.setCurrentRevisionId(afterRevision.getId());
        noteRepository.save(note);

        List<DiffEntry> diff = computeDiff(beforeDocument, afterDocument);
        ApplyResultV1 applyResult = new ApplyResultV1(
            note.getId(),
            note.getNotebook() == null ? null : note.getNotebook().getId(),
            beforeRevision.getId(),
            afterRevision.getId(),
            patchPlan.fallbackToInbox() ? "APPLIED_TO_INBOX" : "APPLIED",
            diff.stream().map(DiffEntry::sectionId).toList()
        );
        ProvenanceV1 provenance = new ProvenanceV1(
            chatEvent.getId(),
            provider.providerName(),
            provider.routerModel(),
            provider.plannerModel(),
            provider.routerPromptVersion(),
            patchPlan.plannerPromptVersion(),
            routePlan.confidence(),
            routePlan.reasonCodes()
        );

        DocumentOperation operation = new DocumentOperation();
        operation.setNote(note);
        operation.setChatEvent(chatEvent);
        operation.setOperationKind(routePlan.intent().name());
        operation.setBeforeRevision(beforeRevision);
        operation.setAfterRevision(afterRevision);
        operation.setProviderName(provider.providerName());
        operation.setModelName(provider.plannerModel());
        operation.setPromptVersion(patchPlan.plannerPromptVersion());
        operation.setRouteConfidence(routePlan.confidence());
        operation.setDiffJson(writeJson(diff));
        operation.setProvenanceJson(writeJson(provenance));
        operation.setCreatedAt(note.getUpdatedAt());
        DocumentOperation savedOperation = documentOperationRepository.save(operation);

        refreshNotebookSummary(note.getNotebook());
        return new MutationResult(
            applyResult,
            toDetailResponse(note),
            diff,
            provenance,
            String.valueOf(savedOperation.getId())
        );
    }

    @Transactional(readOnly = true)
    public List<RevisionHistoryEntryResponse> history(Long noteId) {
        requireNote(noteId);
        return noteRevisionRepository.findByNote_IdOrderByCreatedAtDesc(noteId)
            .stream()
            .map(revision -> new RevisionHistoryEntryResponse(
                revision.getId(),
                revision.getRevisionNumber(),
                revision.getTitle(),
                revision.getSummaryShort(),
                revision.getSourceChatEvent() == null ? null : revision.getSourceChatEvent().getId(),
                revision.getCreatedAt()
            ))
            .toList();
    }

    @Transactional
    public UndoResultResponse undo(Long noteId, Long operationId) {
        Note note = requireNote(noteId);
        DocumentOperation operation = documentOperationRepository.findByIdAndNote_Id(operationId, noteId)
            .orElseThrow(() -> new NotFoundException("Operation not found: " + operationId));
        if (operation.getUndoneAt() != null) {
            throw new ValidationException("Operation already undone: " + operationId);
        }
        NoteRevision beforeRevision = operation.getBeforeRevision();
        if (beforeRevision == null) {
            throw new ValidationException("Operation cannot be undone because no prior revision exists.");
        }
        NoteDocumentV1 beforeDocument = noteDocumentCodec.read(note.getDocumentJson());
        NoteDocumentV1 restoreDocument = noteDocumentCodec.read(beforeRevision.getDocumentJson());
        note.setTitle(beforeRevision.getTitle());
        note.setSummaryShort(beforeRevision.getSummaryShort());
        note.setDocumentJson(beforeRevision.getDocumentJson());
        note.setEditorStateJson(beforeRevision.getEditorStateJson());
        note.setRawText(beforeRevision.getEditorStateJson());
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);

        NoteRevision undoRevision = createRevision(note, null, note.getUpdatedAt());
        note.setCurrentRevisionId(undoRevision.getId());
        noteRepository.save(note);

        List<DiffEntry> diff = computeDiff(beforeDocument, restoreDocument);
        ApplyResultV1 applyResult = new ApplyResultV1(
            note.getId(),
            note.getNotebook() == null ? null : note.getNotebook().getId(),
            operation.getAfterRevision().getId(),
            undoRevision.getId(),
            "UNDONE",
            diff.stream().map(DiffEntry::sectionId).toList()
        );

        DocumentOperation undoOperation = new DocumentOperation();
        undoOperation.setNote(note);
        undoOperation.setOperationKind("UNDO");
        undoOperation.setBeforeRevision(operation.getAfterRevision());
        undoOperation.setAfterRevision(undoRevision);
        undoOperation.setProviderName("system");
        undoOperation.setModelName("undo");
        undoOperation.setPromptVersion("undo");
        undoOperation.setRouteConfidence(1.0);
        undoOperation.setDiffJson(writeJson(diff));
        undoOperation.setProvenanceJson(writeJson(Map.of("undidOperationId", operationId)));
        undoOperation.setCreatedAt(note.getUpdatedAt());
        DocumentOperation savedUndo = documentOperationRepository.save(undoOperation);

        operation.setUndoneAt(note.getUpdatedAt());
        documentOperationRepository.save(operation);
        refreshNotebookSummary(note.getNotebook());
        return new UndoResultResponse(applyResult, toDetailResponse(note), diff, String.valueOf(savedUndo.getId()));
    }

    @Transactional
    public NoteV2Response recomputeSummary(Long noteId, AiWriteProvider provider) {
        Note note = requireNote(noteId);
        NoteDocumentV1 document = noteDocumentCodec.read(note.getDocumentJson());
        note.setSummaryShort(provider.summarize(document).summaryShort());
        note.setUpdatedAt(Instant.now());
        noteRepository.save(note);
        refreshNotebookSummary(note.getNotebook());
        return toDetailResponse(note);
    }

    private Note resolveTargetNote(RoutePlanV1 routePlan, PatchPlanV1 patchPlan) {
        if (routePlan.intent() == RouteIntent.WRITE_EXISTING_NOTE && routePlan.targetNoteId() != null) {
            return requireNote(routePlan.targetNoteId());
        }
        if (routePlan.strategy() == RouteStrategy.NOTEBOOK_INBOX && routePlan.targetNotebookId() != null) {
            return notebookInboxNote(routePlan.targetNotebookId());
        }
        if (routePlan.intent() == RouteIntent.CREATE_NOTE) {
            return createEmptyNote(routePlan.targetNotebookId(), routePlan.targetNoteType(), extractCreateTitle(patchPlan.ops()));
        }
        throw new ValidationException("No target note could be resolved for route intent " + routePlan.intent());
    }

    private Note createEmptyNote(Long notebookId, String noteType, String title) {
        if (notebookId == null) {
            throw new ValidationException("targetNotebookId is required to create a note");
        }
        Notebook notebook = notebookService.getNotebookRequired(notebookId);
        NoteDocumentV1 template = canonicalNoteTemplates.createTemplate(noteType, notebookId, title);
        String editorJson = blockNoteMapper.toEditorJson(template);
        Instant now = Instant.now();
        Note note = new Note(
            NotebookService.DEFAULT_USER_ID,
            notebook,
            editorJson,
            SourceType.TEXT,
            NoteStatus.READY,
            null,
            now,
            now
        );
        populateNote(note, template, editorJson);
        return noteRepository.save(note);
    }

    private Note notebookInboxNote(Long notebookId) {
        Note note = noteRepository.findByUserIdAndNotebook_IdAndTitleIgnoreCase(NotebookService.DEFAULT_USER_ID, notebookId, SYSTEM_INBOX_TITLE)
            .orElseGet(() -> createEmptyNote(notebookId, CanonicalNoteTemplates.GENERIC_NOTE, SYSTEM_INBOX_TITLE));
        if (note.getStatus() != NoteStatus.SYSTEM_HIDDEN) {
            note.setStatus(NoteStatus.SYSTEM_HIDDEN);
            noteRepository.save(note);
        }
        return note;
    }

    private String extractCreateTitle(List<PatchOperation> ops) {
        for (PatchOperation op : ops) {
            if (op.op() == PatchOpType.CREATE_NOTE && op.title() != null && !op.title().isBlank()) {
                return op.title();
            }
        }
        return "Untitled note";
    }

    private NoteDocumentV1 applyOps(NoteDocumentV1 source,
                                    List<PatchOperation> ops,
                                    Long noteId,
                                    Long notebookId,
                                    Long currentRevisionId) {
        String title = source.meta().title();
        String summary = source.meta().summaryShort();
        List<NoteSection> sections = normalizeSections(source.sections());

        for (PatchOperation op : ops) {
            switch (op.op()) {
                case CREATE_NOTE -> {
                    if (op.title() != null && !op.title().isBlank()) {
                        title = op.title().trim();
                    }
                    if (op.summaryShort() != null) {
                        summary = op.summaryShort().trim();
                    }
                }
                case RENAME_NOTE -> title = requireText(op.title(), "title");
                case UPDATE_NOTE_SUMMARY -> summary = requireText(op.summaryShort(), "summaryShort");
                case APPEND_SECTION_CONTENT -> sections = appendSectionContent(sections, requireText(op.sectionId(), "sectionId"), requireText(op.contentMarkdown(), "contentMarkdown"));
                case REPLACE_SECTION_CONTENT -> sections = replaceSectionContent(sections, requireText(op.sectionId(), "sectionId"), requireText(op.contentMarkdown(), "contentMarkdown"));
                case INSERT_SECTION_AFTER -> sections = insertSectionAfter(sections, requireText(op.afterSectionId(), "afterSectionId"), op.sections());
                case DELETE_SECTION -> sections = deleteSection(sections, requireText(op.sectionId(), "sectionId"));
                case REPLACE_NOTE_OUTLINE -> {
                    if (op.sections() == null || op.sections().isEmpty()) {
                        throw new ValidationException("replace_note_outline requires sections");
                    }
                    sections = normalizeSections(op.sections());
                    if (op.title() != null && !op.title().isBlank()) {
                        title = op.title().trim();
                    }
                    if (op.summaryShort() != null && !op.summaryShort().isBlank()) {
                        summary = op.summaryShort().trim();
                    }
                }
            }
        }

        if (summary.isBlank()) {
            summary = summarizeSections(sections);
        }

        return new NoteDocumentV1(
            new NoteDocumentMeta(noteId, title, summary, source.meta().noteType(), source.meta().schemaVersion(), notebookId, currentRevisionId),
            normalizeSections(sections)
        );
    }

    private List<NoteSection> appendSectionContent(List<NoteSection> sections, String sectionId, String contentMarkdown) {
        List<NoteSection> updated = new ArrayList<>();
        boolean found = false;
        for (NoteSection section : sections) {
            if (section.id().equals(sectionId)) {
                String existingContent = normalizeSectionContent(section.contentMarkdown());
                String next = existingContent.isBlank()
                    ? contentMarkdown
                    : existingContent + "\n\n" + contentMarkdown;
                updated.add(new NoteSection(section.id(), section.label(), section.kind(), next));
                found = true;
            } else {
                updated.add(normalizeSection(section));
            }
        }
        if (!found) {
            throw new ValidationException("Unknown sectionId: " + sectionId);
        }
        return updated;
    }

    private List<NoteSection> replaceSectionContent(List<NoteSection> sections, String sectionId, String contentMarkdown) {
        List<NoteSection> updated = new ArrayList<>();
        boolean found = false;
        for (NoteSection section : sections) {
            if (section.id().equals(sectionId)) {
                updated.add(new NoteSection(section.id(), section.label(), section.kind(), contentMarkdown));
                found = true;
            } else {
                updated.add(normalizeSection(section));
            }
        }
        if (!found) {
            throw new ValidationException("Unknown sectionId: " + sectionId);
        }
        return updated;
    }

    private List<NoteSection> insertSectionAfter(List<NoteSection> sections, String afterSectionId, List<NoteSection> newSections) {
        if (newSections == null || newSections.isEmpty()) {
            throw new ValidationException("insert_section_after requires sections");
        }
        List<NoteSection> updated = new ArrayList<>();
        boolean inserted = false;
        for (NoteSection section : sections) {
            updated.add(section);
            if (section.id().equals(afterSectionId)) {
                updated.addAll(newSections);
                inserted = true;
            }
        }
        if (!inserted) {
            throw new ValidationException("Unknown afterSectionId: " + afterSectionId);
        }
        return updated;
    }

    private List<NoteSection> deleteSection(List<NoteSection> sections, String sectionId) {
        List<NoteSection> updated = new ArrayList<>();
        for (NoteSection section : sections) {
            if (!section.id().equals(sectionId)) {
                updated.add(section);
            }
        }
        if (updated.size() == sections.size()) {
            throw new ValidationException("Unknown sectionId: " + sectionId);
        }
        if (updated.isEmpty()) {
            throw new ValidationException("A note must keep at least one section");
        }
        return updated;
    }

    private List<DiffEntry> computeDiff(NoteDocumentV1 before, NoteDocumentV1 after) {
        Map<String, NoteSection> beforeSections = new LinkedHashMap<>();
        for (NoteSection section : before.sections()) {
            beforeSections.put(section.id(), section);
        }
        Map<String, NoteSection> afterSections = new LinkedHashMap<>();
        for (NoteSection section : after.sections()) {
            afterSections.put(section.id(), section);
        }

        List<DiffEntry> diff = new ArrayList<>();
        if (!before.meta().title().equals(after.meta().title())) {
            diff.add(new DiffEntry("title", "Title", "updated", before.meta().title(), after.meta().title()));
        }
        for (Map.Entry<String, NoteSection> entry : afterSections.entrySet()) {
            NoteSection beforeSection = beforeSections.get(entry.getKey());
            NoteSection afterSection = entry.getValue();
            if (NoteSectionVisibility.isHidden(afterSection)) {
                continue;
            }
            if (beforeSection == null) {
                diff.add(new DiffEntry(afterSection.id(), afterSection.label(), "added", "", excerpt(afterSection.contentMarkdown())));
            } else if (!normalizeSectionContent(beforeSection.contentMarkdown()).equals(normalizeSectionContent(afterSection.contentMarkdown()))) {
                diff.add(new DiffEntry(afterSection.id(), afterSection.label(), "updated", excerpt(beforeSection.contentMarkdown()), excerpt(afterSection.contentMarkdown())));
            }
        }
        for (Map.Entry<String, NoteSection> entry : beforeSections.entrySet()) {
            if (NoteSectionVisibility.isHidden(entry.getValue())) {
                continue;
            }
            if (!afterSections.containsKey(entry.getKey())) {
                diff.add(new DiffEntry(entry.getKey(), entry.getValue().label(), "deleted", excerpt(entry.getValue().contentMarkdown()), ""));
            }
        }
        return diff;
    }

    private String excerpt(String value) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 177) + "...";
    }

    private String summarizeSections(List<NoteSection> sections) {
        for (NoteSection section : sections) {
            if (NoteSectionVisibility.isHidden(section)) {
                continue;
            }
            String summary = excerpt(section.contentMarkdown());
            if (!summary.isBlank()) {
                return summary;
            }
        }
        return "";
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " is required");
        }
        return value;
    }

    private List<NoteSection> normalizeSections(List<NoteSection> sections) {
        List<NoteSection> normalized = new ArrayList<>();
        for (NoteSection section : sections) {
            normalized.add(normalizeSection(section));
        }
        return normalized;
    }

    private NoteSection normalizeSection(NoteSection section) {
        return new NoteSection(
            section.id(),
            section.label(),
            section.kind(),
            normalizeSectionContent(section.contentMarkdown())
        );
    }

    private String normalizeSectionContent(String contentMarkdown) {
        return contentMarkdown == null ? "" : contentMarkdown;
    }

    private void validateExpectedRevision(Note note, Long expectedRevisionId) {
        if (expectedRevisionId == null) {
            return;
        }
        if (note.getCurrentRevisionId() == null || !expectedRevisionId.equals(note.getCurrentRevisionId())) {
            throw new ValidationException("Revision mismatch for note " + note.getId());
        }
    }

    private void populateNote(Note note, NoteDocumentV1 document, String editorJson) {
        note.setTitle(document.meta().title());
        note.setSummaryShort(document.meta().summaryShort().isBlank() ? summarizeSections(document.sections()) : document.meta().summaryShort());
        note.setNoteType(canonicalNoteTemplates.normalizeNoteType(document.meta().noteType()));
        note.setSchemaVersion(document.meta().schemaVersion());
        note.setDocumentJson(noteDocumentCodec.write(document));
        note.setEditorStateJson(editorJson);
        note.setRawText(editorJson);
    }

    private NoteRevision createRevision(Note note, ChatEvent sourceChatEvent, Instant createdAt) {
        long nextRevisionNumber = noteRevisionRepository.findTopByNote_IdOrderByRevisionNumberDesc(note.getId())
            .map(existing -> existing.getRevisionNumber() + 1)
            .orElse(1L);
        NoteRevision revision = new NoteRevision();
        revision.setNote(note);
        revision.setRevisionNumber(nextRevisionNumber);
        revision.setTitle(note.getTitle());
        revision.setSummaryShort(note.getSummaryShort());
        revision.setDocumentJson(note.getDocumentJson());
        revision.setEditorStateJson(note.getEditorStateJson());
        revision.setSourceChatEvent(sourceChatEvent);
        revision.setCreatedAt(createdAt);
        return noteRevisionRepository.save(revision);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize operation metadata", e);
        }
    }

    private void refreshNotebookSummary(Notebook notebook) {
        if (notebook == null) {
            return;
        }
        List<Note> notes = noteRepository.findTop50ByUserIdAndNotebook_IdOrderByUpdatedAtDesc(NotebookService.DEFAULT_USER_ID, notebook.getId());
        String summary = notes.stream()
            .filter(this::isVisibleNote)
            .limit(3)
            .map(note -> note.getTitle() + ": " + note.getSummaryShort())
            .reduce((left, right) -> left + " | " + right)
            .orElse(notebook.getDescription());
        notebook.setRoutingSummary(summary);
        notebook.setLastSummaryRefreshAt(Instant.now());
    }

    private boolean isVisibleNote(Note note) {
        return !isSystemInboxNote(note);
    }

    private boolean isSystemInboxNote(Note note) {
        if (note == null) {
            return false;
        }
        if (note.getStatus() == NoteStatus.SYSTEM_HIDDEN) {
            return true;
        }
        return SYSTEM_INBOX_TITLE.equalsIgnoreCase(note.getTitle());
    }

    private Note requireNote(Long noteId) {
        return noteRepository.findById(noteId)
            .orElseThrow(() -> new NotFoundException("Note not found: " + noteId));
    }

    private NoteSummaryV2Response toSummaryResponse(Note note) {
        return new NoteSummaryV2Response(
            note.getId(),
            note.getNotebook() == null ? null : note.getNotebook().getId(),
            note.getTitle(),
            note.getSummaryShort(),
            note.getNoteType(),
            note.getSchemaVersion(),
            note.getCurrentRevisionId(),
            note.getCreatedAt(),
            note.getUpdatedAt()
        );
    }

    private NoteV2Response toDetailResponse(Note note) {
        NoteDocumentV1 document = noteDocumentCodec.read(note.getDocumentJson());
        return new NoteV2Response(
            note.getId(),
            note.getNotebook() == null ? null : note.getNotebook().getId(),
            note.getNotebook() == null ? null : note.getNotebook().getName(),
            note.getTitle(),
            note.getSummaryShort(),
            note.getNoteType(),
            note.getSchemaVersion(),
            note.getCurrentRevisionId(),
            blockNoteMapper.toEditorJson(document),
            document,
            note.getCreatedAt(),
            note.getUpdatedAt()
        );
    }

    public record MutationResult(
        ApplyResultV1 applyResult,
        NoteV2Response updatedNote,
        List<DiffEntry> diff,
        ProvenanceV1 provenance,
        String undoToken
    ) {
    }
}
