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
@Table(name = "note_revisions")
public class NoteRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(name = "revision_number", nullable = false)
    private Long revisionNumber;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "summary_short", nullable = false, length = 500)
    private String summaryShort;

    @Column(name = "document_json", nullable = false, length = 500000)
    private String documentJson;

    @Column(name = "editor_state_json", nullable = false, length = 500000)
    private String editorStateJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_chat_event_id")
    private ChatEvent sourceChatEvent;

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

    public Long getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Long revisionNumber) {
        this.revisionNumber = revisionNumber;
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

    public ChatEvent getSourceChatEvent() {
        return sourceChatEvent;
    }

    public void setSourceChatEvent(ChatEvent sourceChatEvent) {
        this.sourceChatEvent = sourceChatEvent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
