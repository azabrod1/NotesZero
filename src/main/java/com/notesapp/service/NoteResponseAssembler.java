package com.notesapp.service;

import com.notesapp.domain.ClarificationTask;
import com.notesapp.domain.Fact;
import com.notesapp.domain.Note;
import com.notesapp.web.dto.ClarificationTaskResponse;
import com.notesapp.web.dto.FactResponse;
import com.notesapp.web.dto.NoteResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class NoteResponseAssembler {

    private final ClarificationOptionCodec optionCodec;

    public NoteResponseAssembler(ClarificationOptionCodec optionCodec) {
        this.optionCodec = optionCodec;
    }

    public NoteResponse toResponse(Note note, List<Fact> facts, List<ClarificationTask> tasks) {
        NoteResponse response = new NoteResponse();
        response.setId(note.getId());
        response.setNotebookId(note.getNotebook() != null ? note.getNotebook().getId() : null);
        response.setNotebookName(note.getNotebook() != null ? note.getNotebook().getName() : null);
        response.setRawText(note.getRawText());
        response.setSourceType(note.getSourceType());
        response.setStatus(note.getStatus());
        response.setOccurredAt(note.getOccurredAt());
        response.setCreatedAt(note.getCreatedAt());
        response.setFacts(facts.stream()
            .map(this::toFactResponse)
            .collect(Collectors.toList()));
        response.setClarifications(tasks.stream()
            .map(this::toClarificationResponse)
            .collect(Collectors.toList()));
        return response;
    }

    private FactResponse toFactResponse(Fact fact) {
        return new FactResponse(
            fact.getId(),
            fact.getKeyName(),
            fact.getValueType(),
            fact.getValueNumber(),
            fact.getValueText(),
            fact.getValueDatetime(),
            fact.getUnit(),
            fact.getConfidence()
        );
    }

    private ClarificationTaskResponse toClarificationResponse(ClarificationTask task) {
        return new ClarificationTaskResponse(
            task.getId(),
            task.getNote().getId(),
            task.getType(),
            task.getQuestion(),
            optionCodec.decode(task.getOptionsJson()),
            task.getCreatedAt()
        );
    }
}
