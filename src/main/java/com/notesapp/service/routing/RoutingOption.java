package com.notesapp.service.routing;

public class RoutingOption {

    private final Long notebookId;
    private final String notebookName;
    private final double score;

    public RoutingOption(Long notebookId, String notebookName, double score) {
        this.notebookId = notebookId;
        this.notebookName = notebookName;
        this.score = score;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public String getNotebookName() {
        return notebookName;
    }

    public double getScore() {
        return score;
    }
}
