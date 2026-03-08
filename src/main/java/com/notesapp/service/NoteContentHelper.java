package com.notesapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Extracts plain text from note content, handling both BlockNote JSON
 * and legacy HTML formats transparently.
 */
@Component
public class NoteContentHelper {

    private final ObjectMapper objectMapper;

    public NoteContentHelper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Extract plain text from rawText, handling both BlockNote JSON
     * and legacy HTML formats.
     */
    public String toPlainText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String trimmed = rawText.trim();
        if (trimmed.startsWith("[")) {
            return extractTextFromBlockNoteJson(trimmed);
        }
        return stripHtmlTags(trimmed);
    }

    private String extractTextFromBlockNoteJson(String json) {
        try {
            JsonNode blocks = objectMapper.readTree(json);
            StringBuilder sb = new StringBuilder();
            extractText(blocks, sb);
            return sb.toString().replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            // Fallback: treat as plain text
            return json;
        }
    }

    private void extractText(JsonNode node, StringBuilder sb) {
        if (node.isArray()) {
            for (JsonNode child : node) {
                extractText(child, sb);
            }
        } else if (node.isObject()) {
            JsonNode content = node.get("content");
            if (content != null && content.isArray()) {
                for (JsonNode inline : content) {
                    JsonNode text = inline.get("text");
                    if (text != null) {
                        sb.append(text.asText()).append(" ");
                    }
                }
            }
            JsonNode children = node.get("children");
            if (children != null) {
                extractText(children, sb);
            }
        }
    }

    private String stripHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", " ")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
