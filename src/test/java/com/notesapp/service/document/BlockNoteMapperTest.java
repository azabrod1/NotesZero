package com.notesapp.service.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockNoteMapperTest {

    private final CanonicalNoteTemplates templates = new CanonicalNoteTemplates();
    private final BlockNoteMapper mapper = new BlockNoteMapper(new ObjectMapper());

    @Test
    void emptyTemplateDoesNotRenderEmptySectionHeadings() {
        NoteDocumentV1 document = templates.createTemplate(CanonicalNoteTemplates.GENERIC_NOTE, 1L, "Untitled note");

        String editorJson = mapper.toEditorJson(document);

        assertThat(editorJson).contains("Untitled note");
        assertThat(editorJson).doesNotContain("\"Summary\"");
        assertThat(editorJson).doesNotContain("\"Action Items\"");
        assertThat(editorJson).doesNotContain("\"Inbox\"");
    }

    @Test
    void looseEditorParagraphsFlowIntoDefaultBodySection() {
        NoteDocumentV1 template = templates.createTemplate(CanonicalNoteTemplates.GENERIC_NOTE, 1L, "Untitled note");
        String editorJson = """
            [
              {"type":"paragraph","content":[{"type":"text","text":"Vet appointment next week for booster shot.","styles":{}}],"children":[]}
            ]
            """.replace("\n", "").replace("  ", "");

        NoteDocumentV1 document = mapper.fromEditorJson(
            editorJson,
            template,
            CanonicalNoteTemplates.GENERIC_NOTE,
            1L,
            5L,
            9L
        );

        assertThat(document.meta().title()).startsWith("Vet appointment next week");
        assertThat(document.sections().stream()
            .filter(section -> "body".equals(section.id()))
            .findFirst())
            .isPresent()
            .get()
            .extracting(NoteSection::contentMarkdown)
            .asString()
            .contains("booster shot");
    }

    @Test
    void hiddenInboxSectionIsNotRenderedButIsPreservedOnRoundTrip() {
        NoteDocumentV1 template = templates.createTemplate(CanonicalNoteTemplates.GENERIC_NOTE, 1L, "Dog note");
        NoteDocumentV1 existing = new NoteDocumentV1(
            template.meta(),
            template.sections().stream()
                .map(section -> "inbox".equals(section.id())
                    ? new NoteSection(section.id(), section.label(), section.kind(), "- hidden triage item")
                    : ("body".equals(section.id())
                        ? new NoteSection(section.id(), section.label(), section.kind(), "Visible body text")
                        : section))
                .toList()
        );

        String editorJson = mapper.toEditorJson(existing);
        NoteDocumentV1 roundTripped = mapper.fromEditorJson(
            editorJson,
            existing,
            CanonicalNoteTemplates.GENERIC_NOTE,
            1L,
            5L,
            9L
        );

        assertThat(editorJson).contains("Visible body text");
        assertThat(editorJson).doesNotContain("hidden triage item");
        assertThat(roundTripped.sections().stream()
            .filter(section -> "inbox".equals(section.id()))
            .findFirst())
            .isPresent()
            .get()
            .extracting(NoteSection::contentMarkdown)
            .asString()
            .contains("hidden triage item");
    }

    @Test
    void markdownListsRenderAsBlockNoteListBlocks() {
        NoteDocumentV1 template = templates.createTemplate(CanonicalNoteTemplates.PROJECT_NOTE, 1L, "Launch note");
        NoteDocumentV1 document = new NoteDocumentV1(
            template.meta(),
            template.sections().stream()
                .map(section -> switch (section.id()) {
                    case "tasks" -> new NoteSection(section.id(), section.label(), section.kind(),
                        "- [ ] Verify deploy health checks\n- [x] Announce release in Slack");
                    case "decisions" -> new NoteSection(section.id(), section.label(), section.kind(),
                        "- Keep notebook inbox hidden");
                    default -> section;
                })
                .toList()
        );

        String editorJson = mapper.toEditorJson(document);

        assertThat(editorJson).contains("\"type\":\"checkListItem\"");
        assertThat(editorJson).contains("\"type\":\"bulletListItem\"");
        assertThat(editorJson).doesNotContain("Action Items");
    }

    @Test
    void proseFollowedByBulletsRendersAsParagraphThenList() {
        NoteDocumentV1 template = templates.createTemplate(CanonicalNoteTemplates.GENERIC_NOTE, 1L, "Runbook");
        NoteDocumentV1 document = new NoteDocumentV1(
            template.meta(),
            template.sections().stream()
                .map(section -> "body".equals(section.id())
                    ? new NoteSection(section.id(), section.label(), section.kind(), "Deploy order:\n- backend\n- migrations\n- frontend")
                    : section)
                .toList()
        );

        String editorJson = mapper.toEditorJson(document);

        assertThat(editorJson).contains("\"type\":\"paragraph\"");
        assertThat(editorJson).contains("Deploy order:");
        assertThat(editorJson).contains("\"type\":\"bulletListItem\"");
        assertThat(editorJson).contains("backend");
    }

    @Test
    void blockNoteListBlocksRoundTripBackToMarkdown() {
        NoteDocumentV1 template = templates.createTemplate(CanonicalNoteTemplates.GENERIC_NOTE, 1L, "Ops note");
        String editorJson = """
            [
              {"type":"heading","props":{"level":1},"content":[{"type":"text","text":"Ops note","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Body","styles":{}}],"children":[]},
              {"type":"bulletListItem","props":{},"content":[{"type":"text","text":"backend","styles":{}}],"children":[]},
              {"type":"bulletListItem","props":{},"content":[{"type":"text","text":"migrations","styles":{}}],"children":[]},
              {"type":"heading","props":{"level":2},"content":[{"type":"text","text":"Action Items","styles":{}}],"children":[]},
              {"type":"checkListItem","props":{"checked":false},"content":[{"type":"text","text":"Verify prod env vars","styles":{}}],"children":[]},
              {"type":"checkListItem","props":{"checked":true},"content":[{"type":"text","text":"Capture baseline latency","styles":{}}],"children":[]}
            ]
            """.replace("\n", "").replace("  ", "");

        NoteDocumentV1 document = mapper.fromEditorJson(
            editorJson,
            template,
            CanonicalNoteTemplates.GENERIC_NOTE,
            1L,
            7L,
            11L
        );

        assertThat(document.sections().stream()
            .filter(section -> "body".equals(section.id()))
            .findFirst())
            .isPresent()
            .get()
            .extracting(NoteSection::contentMarkdown)
            .asString()
            .contains("- backend\n- migrations");
        assertThat(document.sections().stream()
            .filter(section -> "action_items".equals(section.id()))
            .findFirst())
            .isPresent()
            .get()
            .extracting(NoteSection::contentMarkdown)
            .asString()
            .contains("- [ ] Verify prod env vars")
            .contains("- [x] Capture baseline latency");
    }
}
