package com.notesapp.service.routing;

import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.NoteRoutingIndex;
import com.notesapp.domain.NoteSectionIndex;
import com.notesapp.domain.NotebookRoutingIndex;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.NoteRoutingIndexRepository;
import com.notesapp.repository.NoteSectionIndexRepository;
import com.notesapp.repository.NotebookRoutingIndexRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.service.document.NoteDocumentCodec;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IndexRefreshService {

    private static final Logger log = LoggerFactory.getLogger(IndexRefreshService.class);
    private static final Duration DEBOUNCE_WINDOW = Duration.ofSeconds(30);

    private final NoteRepository noteRepository;
    private final NoteRoutingIndexRepository noteRoutingIndexRepo;
    private final NoteSectionIndexRepository sectionIndexRepo;
    private final NotebookRoutingIndexRepository notebookRoutingIndexRepo;
    private final NotebookService notebookService;
    private final NoteDocumentCodec documentCodec;
    private final DeterministicProfileExtractor profileExtractor;

    private final Map<Long, Instant> lastScheduledNote = new ConcurrentHashMap<>();
    private final Map<Long, Instant> lastScheduledNotebook = new ConcurrentHashMap<>();

    public IndexRefreshService(NoteRepository noteRepository,
                               NoteRoutingIndexRepository noteRoutingIndexRepo,
                               NoteSectionIndexRepository sectionIndexRepo,
                               NotebookRoutingIndexRepository notebookRoutingIndexRepo,
                               NotebookService notebookService,
                               NoteDocumentCodec documentCodec,
                               DeterministicProfileExtractor profileExtractor) {
        this.noteRepository = noteRepository;
        this.noteRoutingIndexRepo = noteRoutingIndexRepo;
        this.sectionIndexRepo = sectionIndexRepo;
        this.notebookRoutingIndexRepo = notebookRoutingIndexRepo;
        this.notebookService = notebookService;
        this.documentCodec = documentCodec;
        this.profileExtractor = profileExtractor;
    }

    /**
     * Schedule an async index refresh for a note, with debouncing.
     */
    @Async
    public void scheduleNoteRefresh(Long noteId) {
        Instant lastScheduled = lastScheduledNote.get(noteId);
        if (lastScheduled != null && Instant.now().isBefore(lastScheduled.plus(DEBOUNCE_WINDOW))) {
            return;
        }
        lastScheduledNote.put(noteId, Instant.now());
        try {
            refreshNoteIndex(noteId);
        } catch (Exception e) {
            log.warn("Failed to refresh note index for noteId={}: {}", noteId, e.getMessage());
        }
    }

    /**
     * Schedule an async index refresh for a notebook, with debouncing.
     */
    @Async
    public void scheduleNotebookRefresh(Long notebookId) {
        if (notebookId == null) return;
        Instant lastScheduled = lastScheduledNotebook.get(notebookId);
        if (lastScheduled != null && Instant.now().isBefore(lastScheduled.plus(DEBOUNCE_WINDOW))) {
            return;
        }
        lastScheduledNotebook.put(notebookId, Instant.now());
        try {
            refreshNotebookIndex(notebookId);
        } catch (Exception e) {
            log.warn("Failed to refresh notebook index for notebookId={}: {}", notebookId, e.getMessage());
        }
    }

    @Transactional
    public void refreshNoteIndex(Long noteId) {
        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null || note.getDocumentJson() == null || note.getDocumentJson().isBlank()) {
            noteRoutingIndexRepo.deleteById(noteId);
            return;
        }

        NoteDocumentV1 document = documentCodec.read(note.getDocumentJson());
        NoteProfileExtraction profile = profileExtractor.extractNoteProfile(document);
        SectionDigestExtraction sectionDigests = profileExtractor.extractSectionDigests(document);

        // Build lexical text for text-based search
        String lexicalText = buildLexicalText(document, profile);

        Instant now = Instant.now();

        // Upsert note routing index
        NoteRoutingIndex index = noteRoutingIndexRepo.findById(noteId).orElse(new NoteRoutingIndex());
        index.setNoteId(noteId);
        index.setNotebookId(note.getNotebook() == null ? null : note.getNotebook().getId());
        index.setSourceRevisionId(note.getCurrentRevisionId());
        index.setNoteFamily(note.getNoteType());
        index.setTitle(note.getTitle());
        index.setScopeSummary(profile.scopeSummary());
        index.setEntityTags(String.join(",", profile.entityTags()));
        index.setAliases(String.join(",", profile.aliases()));
        index.setActivityStatus(profile.activityStatus());
        index.setLexicalText(lexicalText);
        index.setRefreshedAt(now);
        noteRoutingIndexRepo.save(index);

        // Replace section index rows
        sectionIndexRepo.deleteByNoteId(noteId);
        int ordinal = 0;
        for (SectionDigestExtraction.SectionEntry entry : sectionDigests.sections()) {
            NoteSectionIndex sectionIndex = new NoteSectionIndex();
            sectionIndex.setNoteId(noteId);
            sectionIndex.setSectionId(entry.sectionId());
            sectionIndex.setSectionKind(sectionKindForId(document, entry.sectionId()));
            sectionIndex.setOrdinal(ordinal++);
            sectionIndex.setEntityTags(String.join(",", entry.entityTags()));
            sectionIndex.setSectionDigest(entry.digest());
            sectionIndex.setRefreshedAt(now);
            sectionIndexRepo.save(sectionIndex);
        }

        log.debug("Refreshed note index: noteId={}, entities={}, aliases={}", noteId, profile.entityTags().size(), profile.aliases().size());
    }

    @Transactional
    public void refreshNotebookIndex(Long notebookId) {
        Notebook notebook = notebookService.getNotebookRequired(notebookId);
        List<Note> notes = noteRepository.findByNotebook_IdOrderByUpdatedAtDesc(notebookId);

        List<NoteDocumentV1> noteDocuments = new ArrayList<>();
        for (Note note : notes) {
            if (note.getDocumentJson() != null && !note.getDocumentJson().isBlank()) {
                try {
                    noteDocuments.add(documentCodec.read(note.getDocumentJson()));
                } catch (Exception e) {
                    log.warn("Failed to parse document for noteId={}", note.getId());
                }
            }
        }

        NotebookProfileExtraction profile = profileExtractor.extractNotebookProfile(notebook, noteDocuments);

        String lexicalText = buildNotebookLexicalText(notebook, profile);

        NotebookRoutingIndex index = notebookRoutingIndexRepo.findById(notebookId).orElse(new NotebookRoutingIndex());
        index.setNotebookId(notebookId);
        index.setScopeSummary(profile.scopeSummary());
        index.setEntityTags(String.join(",", profile.entityTags()));
        index.setPreferredFamilies(String.join(",", profile.preferredFamilies()));
        index.setLexicalText(lexicalText);
        index.setRefreshedAt(Instant.now());
        notebookRoutingIndexRepo.save(index);

        log.debug("Refreshed notebook index: notebookId={}, entities={}", notebookId, profile.entityTags().size());
    }

    /**
     * Backfill all existing notes and notebooks. Idempotent — safe to rerun.
     */
    @Transactional
    public void backfillAll() {
        log.info("Starting routing index backfill...");
        long startTime = System.nanoTime();

        List<Note> allNotes = noteRepository.findAll();
        int noteCount = 0;
        Set<Long> notebookIds = new LinkedHashSet<>();
        for (Note note : allNotes) {
            try {
                refreshNoteIndex(note.getId());
                noteCount++;
                if (note.getNotebook() != null) {
                    notebookIds.add(note.getNotebook().getId());
                }
            } catch (Exception e) {
                log.warn("Backfill failed for noteId={}: {}", note.getId(), e.getMessage());
            }
        }

        for (Long notebookId : notebookIds) {
            try {
                refreshNotebookIndex(notebookId);
            } catch (Exception e) {
                log.warn("Backfill failed for notebookId={}: {}", notebookId, e.getMessage());
            }
        }

        long elapsed = (System.nanoTime() - startTime) / 1_000_000L;
        log.info("Routing index backfill complete: {} notes, {} notebooks in {}ms", noteCount, notebookIds.size(), elapsed);
    }

    /**
     * Refresh only stale index rows (where source_revision_id doesn't match note's current_revision_id).
     */
    @Transactional
    public int refreshStaleIndexes() {
        List<NoteRoutingIndex> allIndexed = noteRoutingIndexRepo.findAll();
        int refreshed = 0;
        for (NoteRoutingIndex indexed : allIndexed) {
            Note note = noteRepository.findById(indexed.getNoteId()).orElse(null);
            if (note == null) {
                noteRoutingIndexRepo.deleteById(indexed.getNoteId());
                continue;
            }
            if (note.getCurrentRevisionId() != null
                && !note.getCurrentRevisionId().equals(indexed.getSourceRevisionId())) {
                refreshNoteIndex(indexed.getNoteId());
                refreshed++;
            }
        }
        return refreshed;
    }

    private String buildLexicalText(NoteDocumentV1 document, NoteProfileExtraction profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(document.meta().title() == null ? "" : document.meta().title()).append(" ");
        sb.append(String.join(" ", profile.aliases())).append(" ");
        sb.append(String.join(" ", profile.entityTags())).append(" ");
        sb.append(profile.scopeSummary() == null ? "" : profile.scopeSummary()).append(" ");
        for (NoteSection section : document.sections()) {
            if (!NoteSectionVisibility.isHidden(section)) {
                sb.append(section.label()).append(" ");
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT).trim();
    }

    private String buildNotebookLexicalText(Notebook notebook, NotebookProfileExtraction profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(notebook.getName() == null ? "" : notebook.getName()).append(" ");
        sb.append(notebook.getDescription() == null ? "" : notebook.getDescription()).append(" ");
        sb.append(profile.scopeSummary() == null ? "" : profile.scopeSummary()).append(" ");
        sb.append(String.join(" ", profile.entityTags())).append(" ");
        sb.append(notebook.getIncludeExamples() == null ? "" : notebook.getIncludeExamples()).append(" ");
        return sb.toString().toLowerCase(Locale.ROOT).trim();
    }

    private String sectionKindForId(NoteDocumentV1 document, String sectionId) {
        for (NoteSection section : document.sections()) {
            if (section.id().equals(sectionId)) {
                return section.kind();
            }
        }
        return "body";
    }
}
