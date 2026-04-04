package com.notesapp.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "notes.ai.provider=mock",
    "notes.ai.open-ai-api-key=",
    "notes.ai.retrieval-mode=deterministic"
})
class ChatCommitDebugTraceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void commitCanReturnDebugTraceWhenRequested() throws Exception {
        long notebookId = findNotebookId("Work websites");
        String createBody = """
            {
              "notebookId": %d,
              "noteType": "project_note/v1",
              "editorContent": %s
            }
            """.formatted(notebookId, jsonString(projectEditorContent("Launch Memo")));

        String createdResponse = mockMvc.perform(post("/api/v2/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode created = objectMapper.readTree(createdResponse);
        long noteId = created.path("id").asLong();
        long revisionId = created.path("currentRevisionId").asLong();

        String commitBody = """
            {
              "message": "Add a task to compare gpt-5-mini-2025-08-07 and gpt-5.4-2026-03-05 in evals.",
              "selectedNotebookId": %d,
              "selectedNoteId": %d,
              "currentRevisionId": %d,
              "includeDebugTrace": true
            }
            """.formatted(notebookId, noteId, revisionId);

        String commitResponse = mockMvc.perform(post("/api/v2/chat-events/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(commitBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.debugTrace").exists())
            .andExpect(jsonPath("$.debugTrace.retrieval.selectedNotebookId").value(notebookId))
            .andExpect(jsonPath("$.debugTrace.retrieval.selectedNoteId").value(noteId))
            .andExpect(jsonPath("$.debugTrace.retrieval.noteCandidates[0].title").value("Launch Memo"))
            .andExpect(jsonPath("$.debugTrace.route.model").isNotEmpty())
            .andExpect(jsonPath("$.debugTrace.route.promptVersion").isNotEmpty())
            .andExpect(jsonPath("$.debugTrace.patch.model").isNotEmpty())
            .andExpect(jsonPath("$.debugTrace.patch.promptVersion").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode committed = objectMapper.readTree(commitResponse);
        assertThat(committed.path("debugTrace").path("retrieval").path("topNoteGap").isNumber()
            || committed.path("debugTrace").path("retrieval").path("topNoteGap").isNull()).isTrue();
    }

    private long findNotebookId(String notebookName) throws Exception {
        String response = mockMvc.perform(get("/api/v2/notebooks"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode array = objectMapper.readTree(response);
        for (JsonNode node : array) {
            if (notebookName.equals(node.path("name").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("Notebook not found: " + notebookName);
    }

    private String jsonString(String value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String projectEditorContent(String title) {
        return """
            [
              {"type":"heading","props":{"level":1},"content":[{"type":"text","text":"%s","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Summary","styles":{}}],"children":[]},
              {"type":"paragraph","content":[{"type":"text","text":"Planning the rollout.","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Status","styles":{}}],"children":[]},
              {"type":"paragraph","content":[{"type":"text","text":"Drafting implementation steps.","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Decisions","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Tasks","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Open Questions","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Timeline","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"References","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Inbox","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]}
            ]
            """.formatted(title).replace("\n", "").replace("  ", "");
    }
}
