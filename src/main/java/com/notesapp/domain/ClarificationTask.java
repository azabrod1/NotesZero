package com.notesapp.domain;

import com.notesapp.domain.enums.ClarificationStatus;
import com.notesapp.domain.enums.ClarificationType;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clarification_tasks")
public class ClarificationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClarificationType type;

    @Column(nullable = false, length = 512)
    private String question;

    @Column(name = "options_json", nullable = false, length = 2000)
    private String optionsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClarificationStatus status;

    @Column(name = "resolved_option", length = 256)
    private String resolvedOption;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public ClarificationTask() {
    }

    public ClarificationTask(Note note, ClarificationType type, String question, String optionsJson,
                             ClarificationStatus status, Instant createdAt) {
        this.note = note;
        this.type = type;
        this.question = question;
        this.optionsJson = optionsJson;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public ClarificationType getType() {
        return type;
    }

    public void setType(ClarificationType type) {
        this.type = type;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getOptionsJson() {
        return optionsJson;
    }

    public void setOptionsJson(String optionsJson) {
        this.optionsJson = optionsJson;
    }

    public ClarificationStatus getStatus() {
        return status;
    }

    public void setStatus(ClarificationStatus status) {
        this.status = status;
    }

    public String getResolvedOption() {
        return resolvedOption;
    }

    public void setResolvedOption(String resolvedOption) {
        this.resolvedOption = resolvedOption;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
