package com.notesapp.service.aiwrite;

import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.repository.NoteRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.service.document.NoteDocumentCodec;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DeterministicRetrievalService {

    private static final String SYSTEM_INBOX_TITLE = "Inbox";
    private static final double SELECTED_NOTE_STRONG_BOOST = 0.55;
    private static final double SELECTED_NOTE_SOFT_BOOST = 0.04;
    private static final double SELECTED_NOTEBOOK_STRONG_BOOST = 0.32;
    private static final double SELECTED_NOTEBOOK_SOFT_BOOST = 0.08;

    private final NotebookService notebookService;
    private final NoteRepository noteRepository;
    private final NoteDocumentCodec documentCodec;

    public DeterministicRetrievalService(NotebookService notebookService,
                                         NoteRepository noteRepository,
                                         NoteDocumentCodec documentCodec) {
        this.notebookService = notebookService;
        this.noteRepository = noteRepository;
        this.documentCodec = documentCodec;
    }

    @Transactional(readOnly = true)
    public RetrievalBundle retrieve(String message, Long selectedNotebookId, Long selectedNoteId) {
        boolean selectedContextPreferred = prefersSelectedContext(message);
        Set<String> tokens = tokenize(message);
        List<NotebookCandidate> notebookCandidates = notebookCandidates(tokens, selectedNotebookId, selectedContextPreferred);
        List<NoteCandidate> noteCandidates = noteCandidates(message, tokens, selectedNotebookId, selectedNoteId, selectedContextPreferred);
        return new RetrievalBundle(notebookCandidates, noteCandidates);
    }

    @Transactional(readOnly = true)
    public NoteDocumentV1 getSelectedNoteDocument(Long selectedNoteId) {
        if (selectedNoteId == null) {
            return null;
        }
        return noteRepository.findById(selectedNoteId)
            .map(note -> documentCodec.read(note.getDocumentJson()))
            .orElse(null);
    }

    private List<NotebookCandidate> notebookCandidates(Set<String> tokens, Long selectedNotebookId, boolean selectedContextPreferred) {
        List<NotebookCandidate> candidates = new ArrayList<>();
        for (Notebook notebook : notebookService.listNotebookEntities()) {
            double score = 0.05;
            if (selectedNotebookId != null && selectedNotebookId.equals(notebook.getId())) {
                score += selectedContextPreferred ? SELECTED_NOTEBOOK_STRONG_BOOST : SELECTED_NOTEBOOK_SOFT_BOOST;
            }
            score += 0.08 * overlap(tokens, tokenize(notebook.getName()));
            score += 0.05 * overlap(tokens, tokenize(notebook.getDescription()));
            score += 0.10 * overlap(tokens, tokenize(notebook.getRoutingSummary()));
            score += 0.06 * overlap(tokens, tokenize(notebook.getIncludeExamples()));
            score -= 0.04 * overlap(tokens, tokenize(notebook.getExcludeExamples()));
            score += semanticNotebookBias(tokens, notebook);
            candidates.add(new NotebookCandidate(
                notebook.getId(),
                notebook.getName(),
                notebook.getRoutingSummary() == null ? notebook.getDescription() : notebook.getRoutingSummary(),
                clamp(score)
            ));
        }
        candidates.sort(Comparator.comparingDouble(NotebookCandidate::score).reversed());
        return candidates.stream().limit(5).toList();
    }

    private List<NoteCandidate> noteCandidates(String message,
                                               Set<String> tokens,
                                               Long selectedNotebookId,
                                               Long selectedNoteId,
                                               boolean selectedContextPreferred) {
        List<Note> notes = noteRepository.findTop50ByUserIdOrderByUpdatedAtDesc(NotebookService.DEFAULT_USER_ID);
        List<NoteCandidate> candidates = new ArrayList<>();
        for (Note note : notes) {
            if (isSystemInboxNote(note)) {
                continue;
            }
            if (note.getDocumentJson() == null || note.getDocumentJson().isBlank()) {
                continue;
            }
            NoteDocumentV1 document = documentCodec.read(note.getDocumentJson());
            List<NoteSection> visibleSections = document.sections().stream()
                .filter(section -> !NoteSectionVisibility.isHidden(section))
                .toList();
            List<String> sectionLabels = visibleSections.stream()
                .map(NoteSection::label)
                .toList();
            SectionMatch bestSectionMatch = bestSectionMatch(tokens, visibleSections);
            double score = 0.05;
            if (selectedNoteId != null && selectedNoteId.equals(note.getId())) {
                score += selectedContextPreferred ? SELECTED_NOTE_STRONG_BOOST : SELECTED_NOTE_SOFT_BOOST;
            }
            if (selectedNotebookId != null
                && note.getNotebook() != null
                && selectedNotebookId.equals(note.getNotebook().getId())) {
                score += selectedContextPreferred ? 0.18 : 0.03;
            }
            boolean exactTitleMatch = mentionsNoteTitle(message, note.getTitle());
            if (exactTitleMatch) {
                score += 0.90;
            }
            score += 0.10 * overlap(tokens, tokenize(note.getTitle()));
            score += 0.08 * overlap(tokens, tokenize(note.getSummaryShort()));
            score += 0.04 * overlap(tokens, tokenize(String.join(" ", sectionLabels)));
            score += 0.07 * bestSectionMatch.overlap();
            if (note.getNotebook() != null) {
                score += semanticNotebookBias(tokens, note.getNotebook()) * 0.75;
            }
            score += recencyBoost(note.getUpdatedAt());
            candidates.add(new NoteCandidate(
                note.getId(),
                note.getNotebook() == null ? null : note.getNotebook().getId(),
                note.getTitle(),
                note.getSummaryShort(),
                note.getNoteType(),
                sectionLabels,
                bestSectionMatch.snippet(),
                note.getUpdatedAt(),
                clamp(score),
                exactTitleMatch
            ));
        }
        candidates.sort(Comparator.comparingDouble(NoteCandidate::score).reversed());
        return candidates.stream().limit(8).toList();
    }

    private SectionMatch bestSectionMatch(Set<String> messageTokens, List<NoteSection> visibleSections) {
        SectionMatch best = new SectionMatch(0, null);
        for (NoteSection section : visibleSections) {
            if (section.contentMarkdown() == null || section.contentMarkdown().isBlank()) {
                continue;
            }
            int overlap = overlap(messageTokens, tokenize(section.contentMarkdown()));
            if (overlap <= 0) {
                continue;
            }
            String snippet = excerpt(section.contentMarkdown());
            if (overlap > best.overlap()) {
                best = new SectionMatch(overlap, snippet);
            }
        }
        return best;
    }

    private double recencyBoost(Instant updatedAt) {
        if (updatedAt == null) {
            return 0.0;
        }
        long hours = Math.max(0, Duration.between(updatedAt, Instant.now()).toHours());
        if (hours < 4) {
            return 0.12;
        }
        if (hours < 24) {
            return 0.08;
        }
        if (hours < 72) {
            return 0.04;
        }
        return 0.0;
    }

    private int overlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) {
            return tokens;
        }
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        expandSemanticTokens(tokens);
        return tokens;
    }

    private void expandSemanticTokens(Set<String> tokens) {
        Set<String> expanded = new HashSet<>(tokens);
        for (String token : tokens) {
            switch (token) {
                case "toddler", "baby", "child", "kid", "kids", "son", "daughter", "infant", "newborn" -> {
                    expanded.add("family");
                    expanded.add("child");
                    expanded.add("baby");
                    expanded.add("human");
                }
                case "mom", "mommy", "mother", "dad", "daddy", "father", "parent", "husband", "wife", "spouse" -> {
                    expanded.add("family");
                    expanded.add("human");
                }
                case "dog", "dogs", "puppy", "pup", "canine" -> {
                    expanded.add("dog");
                    expanded.add("pet");
                    expanded.add("animal");
                }
                case "cat", "kitty", "feline", "kitten" -> {
                    expanded.add("cat");
                    expanded.add("pet");
                    expanded.add("animal");
                }
                case "vet", "leash", "kibble", "treat", "bark", "litter" -> {
                    expanded.add("pet");
                    expanded.add("animal");
                }
                case "deploy", "deployment", "release", "rollout", "frontend", "backend", "migration", "migrations",
                    "api", "prompt", "routing", "railway", "noteszero", "website", "websites", "prod", "production" -> {
                    expanded.add("work");
                    expanded.add("product");
                    expanded.add("engineering");
                }
                default -> {
                }
            }
        }
        tokens.addAll(expanded);
    }

    private double semanticNotebookBias(Set<String> messageTokens, Notebook notebook) {
        if (notebook == null) {
            return 0.0;
        }

        String profileText = String.join(" ",
            safeLower(notebook.getName()),
            safeLower(notebook.getDescription()),
            safeLower(notebook.getRoutingSummary()),
            safeLower(notebook.getIncludeExamples()),
            safeLower(notebook.getExcludeExamples())
        );

        boolean familyNotebook = containsAny(profileText, "family", "baby", "child", "children", "parent", "human", "toddler");
        boolean petNotebook = containsAny(profileText, "dog", "dogs", "puppy", "pet", "pets", "cat", "kitty", "vet", "canine", "feline");
        boolean workNotebook = containsAny(profileText, "work", "website", "websites", "deploy", "deployment", "product", "api", "engineering", "code");

        boolean familyMessage = containsAny(messageTokens, "family", "child", "baby", "human", "toddler", "parent", "mom", "dad", "son", "daughter");
        boolean petMessage = containsAny(messageTokens, "dog", "pet", "animal", "puppy", "cat", "kitty", "vet", "canine", "feline");
        boolean workMessage = containsAny(messageTokens, "work", "product", "engineering", "deploy", "release", "rollout", "api", "routing", "railway", "noteszero");

        double bias = 0.0;
        if (familyMessage) {
            if (familyNotebook) {
                bias += 0.42;
            }
            if (petNotebook) {
                bias -= 0.20;
            }
            if (workNotebook) {
                bias -= 0.08;
            }
        }
        if (petMessage) {
            if (petNotebook) {
                bias += 0.42;
            }
            if (familyNotebook) {
                bias -= 0.18;
            }
            if (workNotebook) {
                bias -= 0.08;
            }
        }
        if (workMessage) {
            if (workNotebook) {
                bias += 0.42;
            }
            if (familyNotebook || petNotebook) {
                bias -= 0.10;
            }
        }
        return bias;
    }

    private boolean prefersSelectedContext(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT).trim();
        return normalized.contains("this note")
            || normalized.contains("current note")
            || normalized.startsWith("add to the body")
            || normalized.startsWith("add this to")
            || normalized.startsWith("in the body")
            || normalized.startsWith("in this note")
            || normalized.startsWith("rewrite")
            || normalized.startsWith("replace")
            || normalized.startsWith("rename")
            || normalized.startsWith("status update")
            || normalized.startsWith("decision:")
            || normalized.startsWith("open question:")
            || normalized.startsWith("add a task")
            || normalized.startsWith("add task")
            || normalized.startsWith("add an action item")
            || normalized.startsWith("add action item")
            || normalized.startsWith("add a timeline event")
            || normalized.startsWith("add timeline")
            || normalized.startsWith("save this reference")
            || normalized.startsWith("add a reference")
            || normalized.startsWith("what does this note")
            || normalized.startsWith("what does the current note");
    }

    private boolean containsAny(Set<String> tokens, String... values) {
        for (String value : values) {
            if (tokens.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(0.99, score));
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

    private boolean mentionsNoteTitle(String message, String noteTitle) {
        if (message == null || message.isBlank() || noteTitle == null || noteTitle.isBlank()) {
            return false;
        }
        return message.toLowerCase(Locale.ROOT).contains(noteTitle.toLowerCase(Locale.ROOT));
    }

    private String excerpt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) {
            return normalized;
        }
        return normalized.substring(0, 177) + "...";
    }

    private record SectionMatch(
        int overlap,
        String snippet
    ) {
    }
}
