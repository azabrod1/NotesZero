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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "notes.ai.provider=mock",
    "notes.ai.open-ai-api-key="
})
class V2NoteWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void chatCommitUpdatesExistingNoteAndUndoRestoresPreviousRevision() throws Exception {
        long notebookId = findNotebookId("Work websites");
        String createBody = """
            {
              "notebookId": %d,
              "noteType": "project_note/v1",
              "editorContent": %s
            }
            """.formatted(notebookId, jsonString(projectEditorContent("Project Launch")));

        String createdResponse = mockMvc.perform(post("/api/v2/notes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Project Launch"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode created = objectMapper.readTree(createdResponse);
        long noteId = created.path("id").asLong();
        long revisionId = created.path("currentRevisionId").asLong();

        String chatBody = """
            {
              "message": "Add a task to finalize the launch checklist",
              "selectedNotebookId": %d,
              "selectedNoteId": %d,
              "currentRevisionId": %d
            }
            """.formatted(notebookId, noteId, revisionId);

        String commitResponse = mockMvc.perform(post("/api/v2/chat-events/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chatBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.routePlan.intent").value("WRITE_EXISTING_NOTE"))
            .andExpect(jsonPath("$.updatedNote.id").value(noteId))
            .andExpect(jsonPath("$.applyResult.outcome").value("APPLIED"))
            .andExpect(jsonPath("$.undoToken").isNotEmpty())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode committed = objectMapper.readTree(commitResponse);
        long undoOperationId = committed.path("undoToken").asLong();
        JsonNode tasksSection = findSection(committed.path("updatedNote").path("document").path("sections"), "tasks");
        assertThat(tasksSection.path("contentMarkdown").asText()).contains("launch checklist");

        String undoResponse = mockMvc.perform(post("/api/v2/notes/" + noteId + "/undo?operationId=" + undoOperationId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.applyResult.outcome").value("UNDONE"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode undone = objectMapper.readTree(undoResponse);
        JsonNode undoneTasks = findSection(undone.path("updatedNote").path("document").path("sections"), "tasks");
        assertThat(undoneTasks.path("contentMarkdown").asText()).doesNotContain("launch checklist");
    }

    @Test
    void ambiguousChatFallsBackToNotebookInboxNote() throws Exception {
        long notebookId = findNotebookId("Dog notes");

        String chatBody = """
            {
              "message": "Need to remember this at some point",
              "selectedNotebookId": %d
            }
            """.formatted(notebookId);

        String commitResponse = mockMvc.perform(post("/api/v2/chat-events/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chatBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.routePlan.strategy").value("NOTEBOOK_INBOX"))
            .andExpect(jsonPath("$.patchPlan.fallbackToInbox").value(true))
            .andExpect(jsonPath("$.updatedNote.title").value("Inbox"))
            .andExpect(jsonPath("$.applyResult.outcome").value("APPLIED_TO_INBOX"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode committed = objectMapper.readTree(commitResponse);
        assertThat(committed.path("diff").size()).isZero();

        String noteListResponse = mockMvc.perform(get("/api/v2/notes?notebookId=" + notebookId))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode listedNotes = objectMapper.readTree(noteListResponse);
        for (JsonNode node : listedNotes) {
            assertThat(node.path("title").asText()).isNotEqualToIgnoringCase("Inbox");
        }
    }

    @Test
    void rewriteRequestUsesReplaceNoteOutlineAndKeepsNotebook() throws Exception {
        long notebookId = findNotebookId("Family health");
        String createBody = """
            {
              "notebookId": %d,
              "noteType": "generic_note/v1",
              "editorContent": %s
            }
            """.formatted(notebookId, jsonString(genericEditorContent("Fever Tracking")));

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

        String chatBody = """
            {
              "message": "Rewrite this note as a cleaner summary with next steps",
              "selectedNotebookId": %d,
              "selectedNoteId": %d,
              "currentRevisionId": %d
            }
            """.formatted(notebookId, noteId, revisionId);

        mockMvc.perform(post("/api/v2/chat-events/commit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(chatBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patchPlan.ops[0].op").value("REPLACE_NOTE_OUTLINE"))
            .andExpect(jsonPath("$.updatedNote.id").value(noteId))
            .andExpect(jsonPath("$.updatedNote.notebookId").value(notebookId));
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

    private JsonNode findSection(JsonNode sections, String sectionId) {
        for (JsonNode section : sections) {
            if (sectionId.equals(section.path("id").asText())) {
                return section;
            }
        }
        throw new IllegalStateException("Section not found: " + sectionId);
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

    private String genericEditorContent(String title) {
        return """
            [
              {"type":"heading","props":{"level":1},"content":[{"type":"text","text":"%s","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Summary","styles":{}}],"children":[]},
              {"type":"paragraph","content":[{"type":"text","text":"Tracking symptoms over several days.","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Body","styles":{}}],"children":[]},
              {"type":"paragraph","content":[{"type":"text","text":"Fever appeared in the evening.","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Action Items","styles":{}}],"children":[]},
              {"type":"paragraph","content":[{"type":"text","text":"Call pediatrician if symptoms worsen.","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"References","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Inbox","styles":{}}],"children":[]},
              {"type":"paragraph","content":[],"children":[]}
            ]
            """.formatted(title).replace("\n", "").replace("  ", "");
    }
}
