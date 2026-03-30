package com.notesapp.service.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class NoteDocumentCodec {

    private final ObjectMapper objectMapper;

    public NoteDocumentCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(NoteDocumentV1 document) {
        try {
            return objectMapper.writeValueAsString(document);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize note document", e);
        }
    }

    public NoteDocumentV1 read(String json) {
        try {
            return objectMapper.readValue(json, NoteDocumentV1.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize note document", e);
        }
    }
}
