package com.notesapp.service.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoutingDecision {

    private final Long notebookId;
    private final String notebookName;
    private final double confidence;
    private final List<RoutingOption> options;

    public RoutingDecision(Long notebookId, String notebookName, double confidence, List<RoutingOption> options) {
        this.notebookId = notebookId;
        this.notebookName = notebookName;
        this.confidence = confidence;
        this.options = options;
    }

    public static RoutingDecision unresolved(List<RoutingOption> options) {
        return new RoutingDecision(null, null, 0.0, options);
    }

    public Optional<Long> getNotebookId() {
        return Optional.ofNullable(notebookId);
    }

    public Optional<String> getNotebookName() {
        return Optional.ofNullable(notebookName);
    }

    public double getConfidence() {
        return confidence;
    }

    public List<RoutingOption> getOptions() {
        return new ArrayList<>(options);
    }
}
