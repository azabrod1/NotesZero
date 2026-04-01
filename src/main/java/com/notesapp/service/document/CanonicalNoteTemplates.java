package com.notesapp.service.document;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CanonicalNoteTemplates {

    public static final String SCHEMA_VERSION = "v1";
    public static final String PROJECT_NOTE = "project_note/v1";
    public static final String GENERIC_NOTE = "generic_note/v1";
    public static final String ENTITY_LOG = "entity_log/v1";
    public static final String REFERENCE_NOTE = "reference_note/v1";

    public NoteDocumentV1 createTemplate(String noteType, Long notebookId, String title) {
        String resolvedType = normalizeNoteType(noteType);
        List<NoteSection> sections = switch (resolvedType) {
            case PROJECT_NOTE -> projectSections();
            case ENTITY_LOG -> entityLogSections();
            case REFERENCE_NOTE -> referenceNoteSections();
            default -> genericSections();
        };
        return new NoteDocumentV1(
            new NoteDocumentMeta(null, title, "", resolvedType, SCHEMA_VERSION, notebookId, null),
            sections
        );
    }

    public String normalizeNoteType(String noteType) {
        if (PROJECT_NOTE.equals(noteType)) {
            return PROJECT_NOTE;
        }
        if (ENTITY_LOG.equals(noteType)) {
            return ENTITY_LOG;
        }
        if (REFERENCE_NOTE.equals(noteType)) {
            return REFERENCE_NOTE;
        }
        return GENERIC_NOTE;
    }

    private List<NoteSection> projectSections() {
        List<NoteSection> sections = new ArrayList<>();
        sections.add(new NoteSection("summary", "Summary", "summary", ""));
        sections.add(new NoteSection("status", "Status", "status", ""));
        sections.add(new NoteSection("decisions", "Decisions", "dated_log", ""));
        sections.add(new NoteSection("tasks", "Tasks", "task_list", ""));
        sections.add(new NoteSection("open_questions", "Open Questions", "bullet_list", ""));
        sections.add(new NoteSection("timeline", "Timeline", "dated_log", ""));
        sections.add(new NoteSection("references", "References", "link_list", ""));
        sections.add(new NoteSection("inbox", "Inbox", "inbox", ""));
        return sections;
    }

    private List<NoteSection> entityLogSections() {
        List<NoteSection> sections = new ArrayList<>();
        sections.add(new NoteSection("summary", "Summary", "summary", ""));
        sections.add(new NoteSection("key_facts", "Key Facts", "bullet_list", ""));
        sections.add(new NoteSection("recent_updates", "Recent Updates", "dated_log", ""));
        sections.add(new NoteSection("tasks", "Tasks", "task_list", ""));
        sections.add(new NoteSection("references", "References", "link_list", ""));
        sections.add(new NoteSection("inbox", "Inbox", "inbox", ""));
        return sections;
    }

    private List<NoteSection> referenceNoteSections() {
        List<NoteSection> sections = new ArrayList<>();
        sections.add(new NoteSection("summary", "Summary", "summary", ""));
        sections.add(new NoteSection("body", "Content", "body", ""));
        sections.add(new NoteSection("references", "References", "link_list", ""));
        sections.add(new NoteSection("inbox", "Inbox", "inbox", ""));
        return sections;
    }

    private List<NoteSection> genericSections() {
        List<NoteSection> sections = new ArrayList<>();
        sections.add(new NoteSection("summary", "Summary", "summary", ""));
        sections.add(new NoteSection("body", "Body", "body", ""));
        sections.add(new NoteSection("action_items", "Action Items", "task_list", ""));
        sections.add(new NoteSection("references", "References", "link_list", ""));
        sections.add(new NoteSection("inbox", "Inbox", "inbox", ""));
        return sections;
    }
}
