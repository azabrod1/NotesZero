package com.notesapp.web.dto;

import com.notesapp.domain.enums.SourceType;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.Instant;

public class CreateNoteRequest {

    private Long notebookId;

    @NotBlank
    @Size(max = 20000)
    private String rawText;

    @NotNull
    private SourceType sourceType = SourceType.TEXT;

    private Instant occurredAt;

    private String photoFileName;

    private String photoContentType;

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
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

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getPhotoFileName() {
        return photoFileName;
    }

    public void setPhotoFileName(String photoFileName) {
        this.photoFileName = photoFileName;
    }

    public String getPhotoContentType() {
        return photoContentType;
    }

    public void setPhotoContentType(String photoContentType) {
        this.photoContentType = photoContentType;
    }
}
