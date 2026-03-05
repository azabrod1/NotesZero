package com.notesapp.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NoteWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void routesDogNoteAndCreatesTemperatureClarification() throws Exception {
        Long familyHealthId = findNotebookId("Family health");

        String dogBody = "{ \"rawText\": \"my dog just pooped\", \"sourceType\": \"TEXT\" }";
        mockMvc.perform(post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(dogBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("READY"))
            .andExpect(jsonPath("$.notebookName").value("Dog notes"));

        String feverBody = "{ \"rawText\": \"baby had fever of 103\", \"sourceType\": \"TEXT\", \"notebookId\": " + familyHealthId + " }";
        String response = mockMvc.perform(post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(feverBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
            .andExpect(jsonPath("$.clarifications[0].type").value("TEMPERATURE_UNIT"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        long taskId = root.path("clarifications").get(0).path("id").asLong();
        long noteId = root.path("id").asLong();

        String resolveBody = "{ \"selectedOption\": \"F\" }";
        mockMvc.perform(post("/api/v1/clarifications/" + taskId + "/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resolveBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(noteId))
            .andExpect(jsonPath("$.status").value("READY"));

        String queryBody = "{ \"notebookId\": " + familyHealthId + ", \"question\": \"what was max fever\" }";
        String queryResponse = mockMvc.perform(post("/api/v1/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content(queryBody))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode queryJson = objectMapper.readTree(queryResponse);
        assertThat(queryJson.path("answer").asText()).contains("Max temperature is 103");
    }

    @Test
    void createsNotebookClarificationWhenRoutingLowConfidence() throws Exception {
        String body = "{ \"rawText\": \"this happened and I want to remember it\", \"sourceType\": \"TEXT\" }";
        mockMvc.perform(post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("NEEDS_CLARIFICATION"))
            .andExpect(jsonPath("$.clarifications[0].type").value("NOTEBOOK_ASSIGNMENT"));
    }

    @Test
    void updatesNoteAndReextractsFacts() throws Exception {
        Long notebookId = findNotebookId("Dog notes");
        String createBody = "{ \"rawText\": \"I have 2 pies\", \"sourceType\": \"TEXT\", \"notebookId\": " + notebookId + " }";
        String createResponse = mockMvc.perform(post("/api/v1/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        long noteId = objectMapper.readTree(createResponse).path("id").asLong();
        String updateBody = "{ \"rawText\": \"I have 5 pies and ate two apples\", \"notebookId\": " + notebookId + " }";
        mockMvc.perform(put("/api/v1/notes/" + noteId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(noteId))
            .andExpect(jsonPath("$.facts[?(@.keyName=='pie_count')].valueNumber").exists())
            .andExpect(jsonPath("$.facts[?(@.keyName=='apple_eaten')].valueNumber").exists());
    }

    private Long findNotebookId(String notebookName) throws Exception {
        String response = mockMvc.perform(get("/api/v1/notebooks"))
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
