package com.notesapp.web.dto.v2;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public class CommitChatRequest {

    @NotBlank
    @Size(max = 12000)
    private String message;

    private Long selectedNotebookId;
    private Long selectedNoteId;
    private List<Long> recentChatEventIds = List.of();
    private Long currentRevisionId;
    private boolean includeDebugTrace;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getSelectedNotebookId() {
        return selectedNotebookId;
    }

    public void setSelectedNotebookId(Long selectedNotebookId) {
        this.selectedNotebookId = selectedNotebookId;
    }

    public Long getSelectedNoteId() {
        return selectedNoteId;
    }

    public void setSelectedNoteId(Long selectedNoteId) {
        this.selectedNoteId = selectedNoteId;
    }

    public List<Long> getRecentChatEventIds() {
        return recentChatEventIds;
    }

    public void setRecentChatEventIds(List<Long> recentChatEventIds) {
        this.recentChatEventIds = recentChatEventIds;
    }

    public Long getCurrentRevisionId() {
        return currentRevisionId;
    }

    public void setCurrentRevisionId(Long currentRevisionId) {
        this.currentRevisionId = currentRevisionId;
    }

    public boolean isIncludeDebugTrace() {
        return includeDebugTrace;
    }

    public void setIncludeDebugTrace(boolean includeDebugTrace) {
        this.includeDebugTrace = includeDebugTrace;
    }
}
