package com.notesapp.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ClarificationOptionCodec {

    private final ObjectMapper objectMapper;

    public ClarificationOptionCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(List<String> options) {
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode clarification options", e);
        }
    }

    public List<String> decode(String json) {
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
