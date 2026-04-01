package com.notesapp.service.routing;

import com.notesapp.domain.NoteRoutingIndex;
import com.notesapp.domain.NotebookRoutingIndex;
import com.notesapp.domain.NoteSectionIndex;
import com.notesapp.repository.NoteRoutingIndexRepository;
import com.notesapp.repository.NoteSectionIndexRepository;
import com.notesapp.repository.NotebookRoutingIndexRepository;
import com.notesapp.service.NotebookService;
import com.notesapp.service.aiwrite.NoteCandidate;
import com.notesapp.service.aiwrite.NotebookCandidate;
import com.notesapp.service.aiwrite.RetrievalBundle;
import com.notesapp.service.document.NoteDocumentCodec;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import com.notesapp.domain.Note;
import com.notesapp.domain.Notebook;
import com.notesapp.repository.NoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Hybrid retrieval service that combines multiple signals from the routing index
 * to produce ranked note and notebook candidates.
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private static final double SELECTED_NOTE_STRONG_BOOST = 0.55;
    private static final double SELECTED_NOTE_SOFT_BOOST = 0.04;
    private static final double SELECTED_NOTEBOOK_STRONG_BOOST = 0.32;
    private static final double SELECTED_NOTEBOOK_SOFT_BOOST = 0.08;

    private static final double W_ENTITY = 0.30;
    private static final double W_LEXICAL = 0.20;
    private static final double W_SCOPE = 0.15;
    private static final double W_SELECTED = 0.10;
    private static final double W_RECENCY = 0.08;
    private static final double W_FAMILY = 0.05;
    private static final double W_ACTIVITY = 0.05;
    private static final double W_TITLE_MATCH = 0.07;

    private final NoteRoutingIndexRepository noteIndexRepo;
    private final NoteSectionIndexRepository sectionIndexRepo;
    private final NotebookRoutingIndexRepository notebookIndexRepo;
    private final NoteRepository noteRepository;
    private final NotebookService notebookService;
    private final NoteDocumentCodec documentCodec;

    public HybridRetrievalService(NoteRoutingIndexRepository noteIndexRepo,
                                  NoteSectionIndexRepository sectionIndexRepo,
                                  NotebookRoutingIndexRepository notebookIndexRepo,
                                  NoteRepository noteRepository,
                                  NotebookService notebookService,
                                  NoteDocumentCodec documentCodec) {
        this.noteIndexRepo = noteIndexRepo;
        this.sectionIndexRepo = sectionIndexRepo;
        this.notebookIndexRepo = notebookIndexRepo;
        this.noteRepository = noteRepository;
        this.notebookService = notebookService;
        this.documentCodec = documentCodec;
    }

    @Transactional(readOnly = true)
    public RetrievalBundle retrieve(String message, Long selectedNotebookId, Long selectedNoteId) {
        boolean selectedContextPreferred = prefersSelectedContext(message);
        Set<String> messageTokens = tokenize(message);
        List<String> messageEntities = extractMessageEntities(message, messageTokens);

        List<NotebookCandidate> notebookCandidates = retrieveNotebooks(
            message, messageTokens, messageEntities, selectedNotebookId, selectedContextPreferred);
        List<NoteCandidate> noteCandidates = retrieveNotes(
            message, messageTokens, messageEntities, selectedNotebookId, selectedNoteId, selectedContextPreferred);

        return new RetrievalBundle(notebookCandidates, noteCandidates);
    }

    @Transactional(readOnly = true)
    public NoteDocumentV1 getSelectedNoteDocument(Long selectedNoteId) {
        if (selectedNoteId == null) return null;
        return noteRepository.findById(selectedNoteId)
            .map(note -> documentCodec.read(note.getDocumentJson()))
            .orElse(null);
    }

    private List<NotebookCandidate> retrieveNotebooks(String message,
                                                       Set<String> messageTokens,
                                                       List<String> messageEntities,
                                                       Long selectedNotebookId,
                                                       boolean selectedContextPreferred) {
        List<NotebookRoutingIndex> allIndexed = notebookIndexRepo.findAll();
        List<Notebook> allNotebooks = notebookService.listNotebookEntities();
        Map<Long, Notebook> notebookMap = new HashMap<>();
        for (Notebook nb : allNotebooks) {
            notebookMap.put(nb.getId(), nb);
        }

        Map<Long, Double> scores = new LinkedHashMap<>();
        for (NotebookRoutingIndex indexed : allIndexed) {
            double score = 0.05;

            // Entity overlap
            double entityScore = entityOverlap(messageEntities, indexed.entityTagList());
            score += W_ENTITY * entityScore;

            // Lexical overlap
            double lexScore = tokenOverlap(messageTokens, tokenize(indexed.getLexicalText()));
            score += W_LEXICAL * lexScore;

            // Scope summary match
            double scopeScore = tokenOverlap(messageTokens, tokenize(indexed.getScopeSummary()));
            score += W_SCOPE * scopeScore;

            // Selected notebook boost
            if (selectedNotebookId != null && selectedNotebookId.equals(indexed.getNotebookId())) {
                score += selectedContextPreferred ? SELECTED_NOTEBOOK_STRONG_BOOST : SELECTED_NOTEBOOK_SOFT_BOOST;
            }

            scores.put(indexed.getNotebookId(), clamp(score));
        }

        // Also include notebooks without index entries (with base score)
        for (Notebook nb : allNotebooks) {
            if (!scores.containsKey(nb.getId())) {
                double score = 0.05;
                if (selectedNotebookId != null && selectedNotebookId.equals(nb.getId())) {
                    score += selectedContextPreferred ? SELECTED_NOTEBOOK_STRONG_BOOST : SELECTED_NOTEBOOK_SOFT_BOOST;
                }
                scores.put(nb.getId(), clamp(score));
            }
        }

        return scores.entrySet().stream()
            .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
            .limit(5)
            .map(entry -> {
                Notebook nb = notebookMap.get(entry.getKey());
                NotebookRoutingIndex indexed = notebookIndexRepo.findById(entry.getKey()).orElse(null);
                String routingSummary = indexed != null && indexed.getScopeSummary() != null
                    ? indexed.getScopeSummary()
                    : (nb != null ? nb.getRoutingSummary() : null);
                if (routingSummary == null && nb != null) {
                    routingSummary = nb.getDescription();
                }
                return new NotebookCandidate(
                    entry.getKey(),
                    nb != null ? nb.getName() : "Unknown",
                    routingSummary,
                    entry.getValue(),
                    indexed != null ? indexed.entityTagList() : null
                );
            })
            .toList();
    }

    private List<NoteCandidate> retrieveNotes(String message,
                                               Set<String> messageTokens,
                                               List<String> messageEntities,
                                               Long selectedNotebookId,
                                               Long selectedNoteId,
                                               boolean selectedContextPreferred) {
        // Gather candidate note IDs from multiple channels
        Set<Long> candidateNoteIds = new LinkedHashSet<>();

        // Channel 1: Entity tag matches
        for (String entity : messageEntities) {
            for (NoteRoutingIndex match : noteIndexRepo.findByEntityTagContaining(entity)) {
                candidateNoteIds.add(match.getNoteId());
            }
            for (NoteRoutingIndex match : noteIndexRepo.findByAliasContaining(entity)) {
                candidateNoteIds.add(match.getNoteId());
            }
        }

        // Channel 2: Lexical matches on significant tokens
        for (String token : messageTokens) {
            if (token.length() > 3) {
                for (NoteRoutingIndex match : noteIndexRepo.findByLexicalTextContaining(token)) {
                    candidateNoteIds.add(match.getNoteId());
                }
            }
        }

        // Channel 3: Active/recent notes
        for (NoteRoutingIndex active : noteIndexRepo.findActiveOrderByRefreshedAtDesc()) {
            candidateNoteIds.add(active.getNoteId());
            if (candidateNoteIds.size() > 100) break;
        }

        // Channel 4: Selected note/notebook context
        if (selectedNoteId != null) {
            candidateNoteIds.add(selectedNoteId);
        }
        if (selectedNotebookId != null) {
            for (NoteRoutingIndex inNotebook : noteIndexRepo.findByNotebookId(selectedNotebookId)) {
                candidateNoteIds.add(inNotebook.getNoteId());
            }
        }

        // Score each candidate
        Map<Long, ScoredNote> scored = new LinkedHashMap<>();
        for (Long noteId : candidateNoteIds) {
            NoteRoutingIndex indexed = noteIndexRepo.findById(noteId).orElse(null);
            Note note = noteRepository.findById(noteId).orElse(null);
            if (indexed == null || note == null) continue;
            if (isSystemInboxNote(note)) continue;
            if (note.getDocumentJson() == null || note.getDocumentJson().isBlank()) continue;

            double score = 0.05;

            // Entity overlap
            double entityScore = entityOverlap(messageEntities, indexed.entityTagList());
            score += W_ENTITY * entityScore;

            // Lexical overlap on index text
            double lexScore = tokenOverlap(messageTokens, tokenize(indexed.getLexicalText()));
            score += W_LEXICAL * lexScore;

            // Scope summary match
            double scopeScore = tokenOverlap(messageTokens, tokenize(indexed.getScopeSummary()));
            score += W_SCOPE * scopeScore;

            // Exact title match
            boolean exactTitleMatch = mentionsTitle(message, indexed.getTitle());
            if (exactTitleMatch) {
                score += 0.90;
            } else {
                double titleScore = tokenOverlap(messageTokens, tokenize(indexed.getTitle()));
                score += W_TITLE_MATCH * titleScore;
            }

            // Selected context boost
            if (selectedNoteId != null && selectedNoteId.equals(noteId)) {
                score += selectedContextPreferred ? SELECTED_NOTE_STRONG_BOOST : SELECTED_NOTE_SOFT_BOOST;
            }
            if (selectedNotebookId != null && note.getNotebook() != null
                && selectedNotebookId.equals(note.getNotebook().getId())) {
                score += selectedContextPreferred ? 0.18 : 0.03;
            }

            // Recency boost
            score += recencyBoost(note.getUpdatedAt());

            // Activity status bonus
            score += "active".equals(indexed.getActivityStatus()) ? 0.03 : 0.0;

            // Find best matching section
            List<NoteSectionIndex> sectionIndexes = sectionIndexRepo.findByNoteId(noteId);
            NoteSectionIndex bestSection = bestMatchingSection(messageTokens, sectionIndexes);

            scored.put(noteId, new ScoredNote(indexed, note, clamp(score), exactTitleMatch, bestSection));
        }

        return scored.values().stream()
            .sorted(Comparator.comparingDouble(ScoredNote::score).reversed())
            .limit(8)
            .map(sn -> {
                NoteDocumentV1 doc = documentCodec.read(sn.note().getDocumentJson());
                List<String> sectionLabels = doc.sections().stream()
                    .filter(s -> !NoteSectionVisibility.isHidden(s))
                    .map(NoteSection::label)
                    .toList();
                String topSnippet = sn.bestSection() != null ? sn.bestSection().getSectionDigest() : "";
                if ((topSnippet == null || topSnippet.isBlank()) && !doc.sections().isEmpty()) {
                    topSnippet = bestContentSnippet(messageTokens, doc);
                }
                return new NoteCandidate(
                    sn.indexed().getNoteId(),
                    sn.indexed().getNotebookId(),
                    sn.indexed().getTitle(),
                    sn.note().getSummaryShort(),
                    sn.indexed().getNoteFamily(),
                    sectionLabels,
                    topSnippet,
                    sn.note().getUpdatedAt(),
                    sn.score(),
                    sn.exactTitleMatch(),
                    sn.indexed().getScopeSummary(),
                    sn.indexed().entityTagList(),
                    sn.indexed().getActivityStatus()
                );
            })
            .toList();
    }

    /**
     * Extract entities from the message by looking up known entities in the index.
     */
    private List<String> extractMessageEntities(String message, Set<String> tokens) {
        String lower = message.toLowerCase(Locale.ROOT);
        Set<String> found = new LinkedHashSet<>();

        // Check each known entity/alias in the index against the message
        List<NoteRoutingIndex> allIndexed = noteIndexRepo.findAll();
        for (NoteRoutingIndex indexed : allIndexed) {
            for (String entity : indexed.entityTagList()) {
                if (entity.length() >= 3 && lower.contains(entity)) {
                    found.add(entity);
                }
            }
            for (String alias : indexed.aliasList()) {
                if (alias.length() >= 3 && lower.contains(alias)) {
                    found.add(alias);
                }
            }
        }

        // Also add significant message tokens as potential entity candidates
        for (String token : tokens) {
            if (token.length() > 3 && !isStopWord(token)) {
                found.add(token);
            }
        }

        return List.copyOf(found);
    }

    private NoteSectionIndex bestMatchingSection(Set<String> messageTokens, List<NoteSectionIndex> sectionIndexes) {
        NoteSectionIndex best = null;
        double bestScore = 0;
        for (NoteSectionIndex si : sectionIndexes) {
            Set<String> digestTokens = tokenize(si.getSectionDigest());
            Set<String> entityTokens = tokenize(si.getEntityTags());
            double score = tokenOverlap(messageTokens, digestTokens) * 0.7
                + tokenOverlap(messageTokens, entityTokens) * 0.3;
            if (score > bestScore) {
                bestScore = score;
                best = si;
            }
        }
        return best;
    }

    private String bestContentSnippet(Set<String> messageTokens, NoteDocumentV1 doc) {
        String bestSnippet = null;
        int bestOverlap = 0;
        for (NoteSection section : doc.sections()) {
            if (NoteSectionVisibility.isHidden(section)) continue;
            if (section.contentMarkdown() == null || section.contentMarkdown().isBlank()) continue;
            Set<String> sectionTokens = tokenize(section.contentMarkdown());
            int overlap = rawOverlap(messageTokens, sectionTokens);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                bestSnippet = excerpt(section.contentMarkdown());
            }
        }
        return bestSnippet;
    }

    private double entityOverlap(List<String> messageEntities, List<String> noteEntities) {
        if (messageEntities.isEmpty() || noteEntities.isEmpty()) return 0.0;
        int matches = 0;
        for (String msgEntity : messageEntities) {
            for (String noteEntity : noteEntities) {
                if (msgEntity.equals(noteEntity) || msgEntity.contains(noteEntity) || noteEntity.contains(msgEntity)) {
                    matches++;
                    break;
                }
            }
        }
        return Math.min(1.0, (double) matches / Math.max(1, messageEntities.size()));
    }

    private double tokenOverlap(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        int matches = rawOverlap(left, right);
        return Math.min(1.0, (double) matches / Math.max(1, Math.min(left.size(), right.size())));
    }

    private int rawOverlap(Set<String> left, Set<String> right) {
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) count++;
        }
        return count;
    }

    private double recencyBoost(Instant updatedAt) {
        if (updatedAt == null) return 0.0;
        long hours = Math.max(0, Duration.between(updatedAt, Instant.now()).toHours());
        if (hours < 4) return 0.12;
        if (hours < 24) return 0.08;
        if (hours < 72) return 0.04;
        return 0.0;
    }

    private boolean prefersSelectedContext(String message) {
        if (message == null || message.isBlank()) return false;
        String n = message.toLowerCase(Locale.ROOT).trim();
        return n.contains("this note") || n.contains("current note")
            || n.startsWith("add to the body") || n.startsWith("add this to")
            || n.startsWith("in the body") || n.startsWith("in this note")
            || n.startsWith("rewrite") || n.startsWith("replace")
            || n.startsWith("rename") || n.startsWith("status update")
            || n.startsWith("decision:") || n.startsWith("open question:")
            || n.startsWith("add a task") || n.startsWith("add task")
            || n.startsWith("add an action item") || n.startsWith("add action item")
            || n.startsWith("add a timeline event") || n.startsWith("add timeline")
            || n.startsWith("save this reference") || n.startsWith("add a reference")
            || n.startsWith("what does this note") || n.startsWith("what does the current note");
    }

    private boolean mentionsTitle(String message, String title) {
        if (message == null || title == null || title.isBlank()) return false;
        return message.toLowerCase(Locale.ROOT).contains(title.toLowerCase(Locale.ROOT));
    }

    private boolean isSystemInboxNote(Note note) {
        if (note == null) return false;
        if (com.notesapp.domain.enums.NoteStatus.SYSTEM_HIDDEN.equals(note.getStatus())) return true;
        return "Inbox".equalsIgnoreCase(note.getTitle());
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null) return tokens;
        for (String token : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (token.length() > 2) tokens.add(token);
        }
        return tokens;
    }

    private boolean isStopWord(String token) {
        return Set.of(
            "the", "and", "for", "that", "this", "with", "from", "have", "has",
            "been", "will", "just", "about", "also", "into", "some", "than",
            "then", "them", "they", "what", "when", "where", "which", "who"
        ).contains(token);
    }

    private double clamp(double score) {
        return Math.max(0.0, Math.min(0.99, score));
    }

    private String excerpt(String text) {
        if (text == null || text.isBlank()) return null;
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 180) return normalized;
        return normalized.substring(0, 177) + "...";
    }

    private record ScoredNote(
        NoteRoutingIndex indexed,
        Note note,
        double score,
        boolean exactTitleMatch,
        NoteSectionIndex bestSection
    ) {
    }
}
