package com.notesapp.web.dto.v2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpsertNoteV2Request {

    private Long notebookId;
    private String noteType;
    private String title;

    @NotBlank
    @Size(max = 500000)
    private String editorContent;

    private Long currentRevisionId;

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public String getNoteType() {
        return noteType;
    }

    public void setNoteType(String noteType) {
        this.noteType = noteType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEditorContent() {
        return editorContent;
    }

    public void setEditorContent(String editorContent) {
        this.editorContent = editorContent;
    }

    public Long getCurrentRevisionId() {
        return currentRevisionId;
    }

    public void setCurrentRevisionId(Long currentRevisionId) {
        this.currentRevisionId = currentRevisionId;
    }
}
