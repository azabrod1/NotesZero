package com.notesapp.domain;

import com.notesapp.domain.enums.NoteStatus;
import com.notesapp.domain.enums.SourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id")
    private Notebook notebook;

    @Column(name = "raw_text", nullable = false, length = 500000)
    private String rawText;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "summary_short", nullable = false, length = 500)
    private String summaryShort;

    @Column(name = "note_type", nullable = false, length = 64)
    private String noteType;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "document_json", nullable = false, length = 500000)
    private String documentJson;

    @Column(name = "editor_state_json", nullable = false, length = 500000)
    private String editorStateJson;

    @Column(name = "current_revision_id")
    private Long currentRevisionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NoteStatus status;

    @Column(name = "occurred_at")
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Note() {
    }

    public Note(Long userId, Notebook notebook, String rawText, SourceType sourceType, NoteStatus status,
                Instant occurredAt, Instant createdAt, Instant updatedAt) {
        this.userId = userId;
        this.notebook = notebook;
        this.rawText = rawText;
        this.sourceType = sourceType;
        this.status = status;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public void setNotebook(Notebook notebook) {
        this.notebook = notebook;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummaryShort() {
        return summaryShort;
    }

    public void setSummaryShort(String summaryShort) {
        this.summaryShort = summaryShort;
    }

    public String getNoteType() {
        return noteType;
    }

    public void setNoteType(String noteType) {
        this.noteType = noteType;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getDocumentJson() {
        return documentJson;
    }

    public void setDocumentJson(String documentJson) {
        this.documentJson = documentJson;
    }

    public String getEditorStateJson() {
        return editorStateJson;
    }

    public void setEditorStateJson(String editorStateJson) {
        this.editorStateJson = editorStateJson;
    }

    public Long getCurrentRevisionId() {
        return currentRevisionId;
    }

    public void setCurrentRevisionId(Long currentRevisionId) {
        this.currentRevisionId = currentRevisionId;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
