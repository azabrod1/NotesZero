package com.notesapp.web.dto;

import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.domain.enums.SourceType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class NoteResponse {

    private Long id;
    private Long notebookId;
    private String notebookName;
    private String rawText;
    private SourceType sourceType;
    private NoteStatus status;
    private Instant occurredAt;
    private Instant createdAt;
    private List<FactResponse> facts = new ArrayList<>();
    private List<ClarificationTaskResponse> clarifications = new ArrayList<>();

    public NoteResponse() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public String getNotebookName() {
        return notebookName;
    }

    public void setNotebookName(String notebookName) {
        this.notebookName = notebookName;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public NoteStatus getStatus() {
        return status;
    }

    public void setStatus(NoteStatus status) {
        this.status = status;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<FactResponse> getFacts() {
        return facts;
    }

    public void setFacts(List<FactResponse> facts) {
        this.facts = facts;
    }

    public List<ClarificationTaskResponse> getClarifications() {
        return clarifications;
    }

    public void setClarifications(List<ClarificationTaskResponse> clarifications) {
        this.clarifications = clarifications;
    }
}
