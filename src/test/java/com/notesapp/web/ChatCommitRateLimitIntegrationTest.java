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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "notes.ai.provider=mock",
    "notes.ai.open-ai-api-key=",
    "notes.ai.chat-commit-rate-limit-count=3",
    "notes.ai.chat-commit-rate-limit-window-minutes=30"
})
class ChatCommitRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void commitEndpointReturns429AfterConfiguredWindowLimit() throws Exception {
        long notebookId = findNotebookId("Dog notes");
        String requestBody = """
            {
              "message": "remember this for later",
              "selectedNotebookId": %d
            }
            """.formatted(notebookId);

        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/v2/chat-events/commit")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
                    .header("X-Forwarded-For", "203.0.113.10"))
                .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v2/chat-events/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
                .header("X-Forwarded-For", "203.0.113.10"))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.message").value("Too many chat requests. Limit is 3 requests per 30 minutes."));
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
}
