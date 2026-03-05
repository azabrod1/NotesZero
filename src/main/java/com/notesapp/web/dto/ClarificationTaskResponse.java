package com.notesapp.web.dto;

import com.notesapp.domain.enums.ClarificationType;

import java.time.Instant;
import java.util.List;

public class ClarificationTaskResponse {

    private Long id;
    private Long noteId;
    private ClarificationType type;
    private String question;
    private List<String> options;
    private Instant createdAt;

    public ClarificationTaskResponse() {
    }

    public ClarificationTaskResponse(Long id, Long noteId, ClarificationType type, String question, List<String> options,
                                     Instant createdAt) {
        this.id = id;
        this.noteId = noteId;
        this.type = type;
        this.question = question;
        this.options = options;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getNoteId() {
        return noteId;
    }

    public ClarificationType getType() {
        return type;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
