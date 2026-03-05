package com.notesapp.web.dto;

import java.util.ArrayList;
import java.util.List;

public class QueryResponse {

    private String answer;
    private List<Long> citedNoteIds = new ArrayList<>();

    public QueryResponse() {
    }

    public QueryResponse(String answer, List<Long> citedNoteIds) {
        this.answer = answer;
        this.citedNoteIds = citedNoteIds;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<Long> getCitedNoteIds() {
        return citedNoteIds;
    }

    public void setCitedNoteIds(List<Long> citedNoteIds) {
        this.citedNoteIds = citedNoteIds;
    }
}
