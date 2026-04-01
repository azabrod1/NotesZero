package com.notesapp.service.routing;

import com.notesapp.domain.Notebook;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts note/notebook profiles using deterministic heuristics.
 * No LLM calls — fast and free. Used as default and fallback.
 */
@Component
public class DeterministicProfileExtractor {

    private static final Pattern CAPITALIZED_PHRASE = Pattern.compile("\\b([A-Z][a-z]{2,}(?:\\s+[A-Z][a-z]{2,})*)\\b");
    private static final Pattern QUOTED_PHRASE = Pattern.compile("\"([^\"]{2,40})\"");

    public NoteProfileExtraction extractNoteProfile(NoteDocumentV1 document) {
        String title = document.meta().title() == null ? "" : document.meta().title();
        String summary = document.meta().summaryShort() == null ? "" : document.meta().summaryShort();

        StringBuilder allText = new StringBuilder();
        allText.append(title).append(" ").append(summary).append(" ");
        for (NoteSection section : document.sections()) {
            if (!NoteSectionVisibility.isHidden(section) && section.contentMarkdown() != null) {
                allText.append(section.contentMarkdown()).append(" ");
            }
        }
        String fullText = allText.toString();

        Set<String> entities = extractEntities(fullText);
        List<String> aliases = buildAliases(title, entities);
        String scopeSummary = buildScopeSummary(title, summary, document.meta().noteType(), entities);
        String activityStatus = inferActivityStatus(document);

        return new NoteProfileExtraction(scopeSummary, List.copyOf(entities), aliases, activityStatus);
    }

    public NotebookProfileExtraction extractNotebookProfile(Notebook notebook, List<NoteDocumentV1> noteDocuments) {
        String name = notebook.getName() == null ? "" : notebook.getName();
        String desc = notebook.getDescription() == null ? "" : notebook.getDescription();

        Set<String> allEntities = new LinkedHashSet<>();
        Set<String> families = new LinkedHashSet<>();
        for (NoteDocumentV1 doc : noteDocuments) {
            NoteProfileExtraction noteProfile = extractNoteProfile(doc);
            allEntities.addAll(noteProfile.entityTags());
            if (doc.meta().noteType() != null) {
                families.add(doc.meta().noteType());
            }
        }

        String include = notebook.getIncludeExamples() == null ? "" : notebook.getIncludeExamples();
        String exclude = notebook.getExcludeExamples() == null ? "" : notebook.getExcludeExamples();
        String scopeSummary = buildNotebookScope(name, desc, include, exclude);

        return new NotebookProfileExtraction(scopeSummary, List.copyOf(allEntities), List.copyOf(families));
    }

    public SectionDigestExtraction extractSectionDigests(NoteDocumentV1 document) {
        List<SectionDigestExtraction.SectionEntry> entries = new ArrayList<>();
        for (NoteSection section : document.sections()) {
            if (NoteSectionVisibility.isHidden(section)) continue;
            String content = section.contentMarkdown();
            if (content == null || content.isBlank()) {
                entries.add(new SectionDigestExtraction.SectionEntry(section.id(), "", List.of()));
                continue;
            }
            String digest = truncate(content.replaceAll("\\s+", " ").trim(), 300);
            Set<String> sectionEntities = extractEntities(content);
            entries.add(new SectionDigestExtraction.SectionEntry(section.id(), digest, List.copyOf(sectionEntities)));
        }
        return new SectionDigestExtraction(entries);
    }

    private Set<String> extractEntities(String text) {
        Set<String> entities = new LinkedHashSet<>();

        // Extract capitalized multi-word phrases (proper nouns)
        Matcher capMatcher = CAPITALIZED_PHRASE.matcher(text);
        while (capMatcher.find()) {
            String phrase = capMatcher.group(1).trim();
            // Filter out common non-entity phrases
            if (!isCommonPhrase(phrase) && phrase.length() >= 3) {
                entities.add(phrase.toLowerCase(Locale.ROOT));
            }
        }

        // Extract quoted phrases
        Matcher quotedMatcher = QUOTED_PHRASE.matcher(text);
        while (quotedMatcher.find()) {
            entities.add(quotedMatcher.group(1).toLowerCase(Locale.ROOT).trim());
        }

        return entities;
    }

    private boolean isCommonPhrase(String phrase) {
        String lower = phrase.toLowerCase(Locale.ROOT);
        return Set.of(
            "the", "this", "that", "summary", "status", "body", "tasks", "timeline",
            "decisions", "references", "inbox", "action items", "open questions",
            "key facts", "recent updates", "note", "notes", "project", "updated",
            "created", "january", "february", "march", "april", "may", "june",
            "july", "august", "september", "october", "november", "december",
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"
        ).contains(lower);
    }

    private List<String> buildAliases(String title, Set<String> entities) {
        Set<String> aliases = new LinkedHashSet<>();
        if (title != null && !title.isBlank()) {
            aliases.add(title.toLowerCase(Locale.ROOT));
            // Add individual significant words from title
            for (String word : title.toLowerCase(Locale.ROOT).split("\\s+")) {
                if (word.length() > 3 && !isCommonPhrase(word)) {
                    aliases.add(word);
                }
            }
        }
        aliases.addAll(entities);
        return List.copyOf(aliases);
    }

    private String buildScopeSummary(String title, String summary, String noteType, Set<String> entities) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("Tracks ").append(title);
        }
        if (!entities.isEmpty()) {
            sb.append(". Entities: ").append(String.join(", ", entities.stream().limit(5).toList()));
        }
        if (summary != null && !summary.isBlank()) {
            sb.append(". ").append(truncate(summary, 100));
        }
        return truncate(sb.toString(), 300);
    }

    private String buildNotebookScope(String name, String description, String include, String exclude) {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!description.isBlank()) {
            sb.append(": ").append(truncate(description, 150));
        }
        if (!include.isBlank()) {
            sb.append(". Includes: ").append(truncate(include, 100));
        }
        if (!exclude.isBlank()) {
            sb.append(". Excludes: ").append(truncate(exclude, 80));
        }
        return truncate(sb.toString(), 500);
    }

    private String inferActivityStatus(NoteDocumentV1 document) {
        boolean hasOpenTasks = false;
        boolean hasContent = false;
        for (NoteSection section : document.sections()) {
            if (section.contentMarkdown() != null && !section.contentMarkdown().isBlank()) {
                hasContent = true;
            }
            if (("task_list".equals(section.kind())) && section.contentMarkdown() != null) {
                if (section.contentMarkdown().contains("- [ ]")) {
                    hasOpenTasks = true;
                }
            }
        }
        if (hasOpenTasks) return "active";
        if (!hasContent) return "dormant";
        return "active";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }
}
