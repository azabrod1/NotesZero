package com.notesapp.service.routing;

import com.notesapp.domain.Notebook;
import com.notesapp.domain.RoutingFeedback;

import java.util.ArrayList;
import java.util.List;

public class RoutingInput {

    private final String rawText;
    private final List<Notebook> notebooks;
    private final List<RoutingFeedback> feedback;

    public RoutingInput(String rawText, List<Notebook> notebooks, List<RoutingFeedback> feedback) {
        this.rawText = rawText;
        this.notebooks = notebooks;
        this.feedback = feedback;
    }

    public String getRawText() {
        return rawText;
    }

    public List<Notebook> getNotebooks() {
        return new ArrayList<>(notebooks);
    }

    public List<RoutingFeedback> getFeedback() {
        return new ArrayList<>(feedback);
    }
}
