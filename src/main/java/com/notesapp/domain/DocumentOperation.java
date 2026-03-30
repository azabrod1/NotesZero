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
@Table(name = "document_operations")
public class DocumentOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_event_id")
    private ChatEvent chatEvent;

    @Column(name = "operation_kind", nullable = false, length = 64)
    private String operationKind;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "before_revision_id")
    private NoteRevision beforeRevision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "after_revision_id", nullable = false)
    private NoteRevision afterRevision;

    @Column(name = "provider_name", nullable = false, length = 64)
    private String providerName;

    @Column(name = "model_name", nullable = false, length = 128)
    private String modelName;

    @Column(name = "prompt_version", nullable = false, length = 128)
    private String promptVersion;

    @Column(name = "route_confidence", nullable = false)
    private double routeConfidence;

    @Column(name = "diff_json", nullable = false, length = 500000)
    private String diffJson;

    @Column(name = "provenance_json", nullable = false, length = 500000)
    private String provenanceJson;

    @Column(name = "undone_at")
    private Instant undoneAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public ChatEvent getChatEvent() {
        return chatEvent;
    }

    public void setChatEvent(ChatEvent chatEvent) {
        this.chatEvent = chatEvent;
    }

    public String getOperationKind() {
        return operationKind;
    }

    public void setOperationKind(String operationKind) {
        this.operationKind = operationKind;
    }

    public NoteRevision getBeforeRevision() {
        return beforeRevision;
    }

    public void setBeforeRevision(NoteRevision beforeRevision) {
        this.beforeRevision = beforeRevision;
    }

    public NoteRevision getAfterRevision() {
        return afterRevision;
    }

    public void setAfterRevision(NoteRevision afterRevision) {
        this.afterRevision = afterRevision;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public double getRouteConfidence() {
        return routeConfidence;
    }

    public void setRouteConfidence(double routeConfidence) {
        this.routeConfidence = routeConfidence;
    }

    public String getDiffJson() {
        return diffJson;
    }

    public void setDiffJson(String diffJson) {
        this.diffJson = diffJson;
    }

    public String getProvenanceJson() {
        return provenanceJson;
    }

    public void setProvenanceJson(String provenanceJson) {
        this.provenanceJson = provenanceJson;
    }

    public Instant getUndoneAt() {
        return undoneAt;
    }

    public void setUndoneAt(Instant undoneAt) {
        this.undoneAt = undoneAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
