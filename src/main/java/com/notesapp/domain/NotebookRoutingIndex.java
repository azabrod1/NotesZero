package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "notebook_routing_index")
public class NotebookRoutingIndex {

    @Id
    @Column(name = "notebook_id")
    private Long notebookId;

    @Column(name = "scope_summary", length = 500)
    private String scopeSummary;

    @Column(name = "entity_tags", length = 4000)
    private String entityTags;

    @Column(name = "preferred_families", length = 500)
    private String preferredFamilies;

    @Column(name = "lexical_text", length = 500000)
    private String lexicalText;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    public NotebookRoutingIndex() {
    }

    public Long getNotebookId() { return notebookId; }
    public void setNotebookId(Long notebookId) { this.notebookId = notebookId; }

    public String getScopeSummary() { return scopeSummary; }
    public void setScopeSummary(String scopeSummary) { this.scopeSummary = scopeSummary; }

    public String getEntityTags() { return entityTags; }
    public void setEntityTags(String entityTags) { this.entityTags = entityTags; }

    public String getPreferredFamilies() { return preferredFamilies; }
    public void setPreferredFamilies(String preferredFamilies) { this.preferredFamilies = preferredFamilies; }

    public String getLexicalText() { return lexicalText; }
    public void setLexicalText(String lexicalText) { this.lexicalText = lexicalText; }

    public Instant getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(Instant refreshedAt) { this.refreshedAt = refreshedAt; }

    public java.util.List<String> entityTagList() {
        if (entityTags == null || entityTags.isBlank()) return java.util.List.of();
        return java.util.Arrays.stream(entityTags.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
