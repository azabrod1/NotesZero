package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "note_routing_index")
public class NoteRoutingIndex {

    @Id
    @Column(name = "note_id")
    private Long noteId;

    @Column(name = "notebook_id")
    private Long notebookId;

    @Column(name = "source_revision_id")
    private Long sourceRevisionId;

    @Column(name = "note_family", nullable = false, length = 64)
    private String noteFamily;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "scope_summary", length = 300)
    private String scopeSummary;

    @Column(name = "entity_tags", length = 4000)
    private String entityTags;

    @Column(name = "aliases", length = 2000)
    private String aliases;

    @Column(name = "activity_status", nullable = false, length = 20)
    private String activityStatus = "active";

    @Column(name = "lexical_text", length = 500000)
    private String lexicalText;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    public NoteRoutingIndex() {
    }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }

    public Long getNotebookId() { return notebookId; }
    public void setNotebookId(Long notebookId) { this.notebookId = notebookId; }

    public Long getSourceRevisionId() { return sourceRevisionId; }
    public void setSourceRevisionId(Long sourceRevisionId) { this.sourceRevisionId = sourceRevisionId; }

    public String getNoteFamily() { return noteFamily; }
    public void setNoteFamily(String noteFamily) { this.noteFamily = noteFamily; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getScopeSummary() { return scopeSummary; }
    public void setScopeSummary(String scopeSummary) { this.scopeSummary = scopeSummary; }

    public String getEntityTags() { return entityTags; }
    public void setEntityTags(String entityTags) { this.entityTags = entityTags; }

    public String getAliases() { return aliases; }
    public void setAliases(String aliases) { this.aliases = aliases; }

    public String getActivityStatus() { return activityStatus; }
    public void setActivityStatus(String activityStatus) { this.activityStatus = activityStatus; }

    public String getLexicalText() { return lexicalText; }
    public void setLexicalText(String lexicalText) { this.lexicalText = lexicalText; }

    public Instant getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(Instant refreshedAt) { this.refreshedAt = refreshedAt; }

    /**
     * Parse comma-separated entity_tags into a list.
     */
    public java.util.List<String> entityTagList() {
        if (entityTags == null || entityTags.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(entityTags.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }

    /**
     * Parse comma-separated aliases into a list.
     */
    public java.util.List<String> aliasList() {
        if (aliases == null || aliases.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(aliases.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
