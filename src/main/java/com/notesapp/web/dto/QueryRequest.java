package com.notesapp.web.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class QueryRequest {

    @NotNull
    private Long notebookId;

    @NotBlank
    private String question;

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
