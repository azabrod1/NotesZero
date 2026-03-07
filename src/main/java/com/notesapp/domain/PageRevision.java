package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "page_revisions")
public class PageRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @Column(name = "content_snapshot", nullable = false, length = 20000)
    private String contentSnapshot;

    @Column(name = "source_note_ids", nullable = false, length = 1000)
    private String sourceNoteIds;

    @Column(name = "model_confidence", nullable = false)
    private double modelConfidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PageRevision() {
    }

    public PageRevision(Page page, String contentSnapshot, String sourceNoteIds, double modelConfidence, Instant createdAt) {
        this.page = page;
        this.contentSnapshot = contentSnapshot;
        this.sourceNoteIds = sourceNoteIds;
        this.modelConfidence = modelConfidence;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Page getPage() {
        return page;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public String getContentSnapshot() {
        return contentSnapshot;
    }

    public void setContentSnapshot(String contentSnapshot) {
        this.contentSnapshot = contentSnapshot;
    }

    public String getSourceNoteIds() {
        return sourceNoteIds;
    }

    public void setSourceNoteIds(String sourceNoteIds) {
        this.sourceNoteIds = sourceNoteIds;
    }

    public double getModelConfidence() {
        return modelConfidence;
    }

    public void setModelConfidence(double modelConfidence) {
        this.modelConfidence = modelConfidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

