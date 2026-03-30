package com.notesapp.service.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BlockNoteMapper {

    private static final Pattern CHECKLIST_ITEM = Pattern.compile("^- \\[( |x|X)]\\s+(.+)$");
    private static final Pattern BULLET_ITEM = Pattern.compile("^-\\s+(.+)$");
    private static final Pattern NUMBERED_ITEM = Pattern.compile("^(\\d+)\\.\\s+(.+)$");

    private final ObjectMapper objectMapper;

    public BlockNoteMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toEditorJson(NoteDocumentV1 document) {
        ArrayNode blocks = objectMapper.createArrayNode();
        blocks.add(headingBlock(document.meta().title(), 1));
        int visibleSections = 0;
        for (NoteSection section : document.sections()) {
            if (NoteSectionVisibility.isHidden(section)) {
                continue;
            }
            if (section.contentMarkdown() == null || section.contentMarkdown().isBlank()) {
                continue;
            }
            visibleSections++;
            blocks.add(headingBlock(section.label(), 2));
            appendMarkdownBlocks(blocks, section.contentMarkdown());
        }
        if (visibleSections == 0) {
            blocks.add(paragraphBlock(""));
        }
        return blocks.toString();
    }

    public NoteDocumentV1 fromEditorJson(String editorJson,
                                         NoteDocumentV1 existing,
                                         String noteType,
                                         Long notebookId,
                                         Long noteId,
                                         Long currentRevisionId) {
        try {
            JsonNode root = objectMapper.readTree(editorJson);
            List<JsonNode> blocks = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(blocks::add);
            }
            String title = firstHeadingText(blocks);
            if (title.isBlank()) {
                title = firstBodyText(blocks);
            }
            List<NoteSection> sections = extractSections(blocks, existing.sections());
            String summary = sections.isEmpty() ? "" : summarize(sections.get(0).contentMarkdown());
            return new NoteDocumentV1(
                new NoteDocumentMeta(
                    noteId,
                    title.isBlank() ? existing.meta().title() : title,
                    summary,
                    noteType,
                    CanonicalNoteTemplates.SCHEMA_VERSION,
                    notebookId,
                    currentRevisionId
                ),
                sections
            );
        } catch (Exception e) {
            throw new IllegalArgumentException("Editor content is not valid BlockNote JSON", e);
        }
    }

    private List<NoteSection> extractSections(List<JsonNode> blocks, List<NoteSection> fallbackSections) {
        List<SectionCapture> captures = new ArrayList<>();
        List<JsonNode> content = new ArrayList<>();
        List<JsonNode> looseContent = new ArrayList<>();
        String label = null;

        for (JsonNode block : blocks) {
            String type = block.path("type").asText("");
            int level = block.path("props").path("level").asInt(-1);
            if ("heading".equals(type) && level == 1) {
                continue;
            }
            if ("heading".equals(type) && level == 2) {
                if (label != null) {
                    captures.add(new SectionCapture(label, new ArrayList<>(content)));
                }
                label = inlineText(block).trim();
                content = new ArrayList<>();
                continue;
            }
            if (label == null) {
                looseContent.add(block);
            } else {
                content.add(block);
            }
        }

        if (label != null) {
            captures.add(new SectionCapture(label, new ArrayList<>(content)));
        }

        String looseMarkdown = blocksToMarkdown(looseContent);
        if (captures.isEmpty() && looseMarkdown.isBlank()) {
            return fallbackSections;
        }

        Map<String, NoteSection> resolvedById = new LinkedHashMap<>();
        for (NoteSection fallback : fallbackSections) {
            String preservedContent = NoteSectionVisibility.isHidden(fallback) ? fallback.contentMarkdown() : "";
            resolvedById.put(
                fallback.id(),
                new NoteSection(fallback.id(), fallback.label(), fallback.kind(), preservedContent)
            );
        }

        Set<String> matchedFallbackIds = new LinkedHashSet<>();
        Set<String> usedIds = new LinkedHashSet<>(resolvedById.keySet());
        if (!looseMarkdown.isBlank()) {
            NoteSection defaultSection = defaultSection(fallbackSections);
            if (defaultSection != null) {
                resolvedById.put(
                    defaultSection.id(),
                    new NoteSection(defaultSection.id(), defaultSection.label(), defaultSection.kind(), looseMarkdown)
                );
                matchedFallbackIds.add(defaultSection.id());
            }
        }

        List<NoteSection> extraSections = new ArrayList<>();
        for (SectionCapture capture : captures) {
            String markdown = blocksToMarkdown(capture.content());
            NoteSection fallback = findFallbackSection(capture.label(), fallbackSections, matchedFallbackIds);
            if (fallback != null) {
                resolvedById.put(
                    fallback.id(),
                    new NoteSection(fallback.id(), fallback.label(), fallback.kind(), markdown)
                );
                matchedFallbackIds.add(fallback.id());
                continue;
            }

            String resolvedId = dedupeId(slugify(capture.label()), usedIds);
            extraSections.add(new NoteSection(resolvedId, capture.label(), "body", markdown));
        }

        List<NoteSection> resolved = new ArrayList<>(resolvedById.values());
        if (extraSections.isEmpty()) {
            return resolved;
        }

        int inboxIndex = -1;
        for (int i = 0; i < resolved.size(); i++) {
            if ("inbox".equals(resolved.get(i).id())) {
                inboxIndex = i;
                break;
            }
        }
        if (inboxIndex < 0) {
            resolved.addAll(extraSections);
            return resolved;
        }

        List<NoteSection> withExtras = new ArrayList<>();
        withExtras.addAll(resolved.subList(0, inboxIndex));
        withExtras.addAll(extraSections);
        withExtras.addAll(resolved.subList(inboxIndex, resolved.size()));
        return withExtras;
    }

    private NoteSection defaultSection(List<NoteSection> fallbackSections) {
        for (NoteSection section : fallbackSections) {
            if ("body".equals(section.id())) {
                return section;
            }
        }
        for (NoteSection section : fallbackSections) {
            if ("summary".equals(section.id())) {
                return section;
            }
        }
        if (fallbackSections.isEmpty()) {
            return null;
        }
        return fallbackSections.get(0);
    }

    private NoteSection findFallbackSection(String label, List<NoteSection> fallbackSections, Set<String> matchedFallbackIds) {
        String normalizedLabel = normalizeLabel(label);
        String slug = slugify(label);
        for (NoteSection fallback : fallbackSections) {
            if (matchedFallbackIds.contains(fallback.id())) {
                continue;
            }
            if (normalizeLabel(fallback.label()).equals(normalizedLabel) || fallback.id().equals(slug)) {
                return fallback;
            }
        }
        return null;
    }

    private String dedupeId(String candidateId, Set<String> usedIds) {
        String base = candidateId == null || candidateId.isBlank() ? "section" : candidateId;
        String current = base;
        int suffix = 2;
        while (usedIds.contains(current)) {
            current = base + "_" + suffix;
            suffix++;
        }
        usedIds.add(current);
        return current;
    }

    private String blocksToMarkdown(List<JsonNode> blocks) {
        List<String> paragraphs = new ArrayList<>();
        List<String> listLines = new ArrayList<>();
        String activeListType = null;

        for (JsonNode block : blocks) {
            String blockType = block.path("type").asText("");
            String markdownLine = blockToMarkdownLine(block).trim();
            if (markdownLine.isBlank()) {
                continue;
            }

            if (isListBlockType(blockType)) {
                if (activeListType != null && !activeListType.equals(blockType) && !listLines.isEmpty()) {
                    paragraphs.add(String.join("\n", listLines));
                    listLines.clear();
                }
                activeListType = blockType;
                listLines.add(markdownLine);
                continue;
            }

            if (!listLines.isEmpty()) {
                paragraphs.add(String.join("\n", listLines));
                listLines.clear();
                activeListType = null;
            }
            paragraphs.add(markdownLine);
        }

        if (!listLines.isEmpty()) {
            paragraphs.add(String.join("\n", listLines));
        }

        return String.join("\n\n", paragraphs);
    }

    private String firstHeadingText(List<JsonNode> blocks) {
        for (JsonNode block : blocks) {
            if ("heading".equals(block.path("type").asText(""))
                && block.path("props").path("level").asInt(-1) == 1) {
                return inlineText(block).trim();
            }
        }
        return "";
    }

    private String firstBodyText(List<JsonNode> blocks) {
        for (JsonNode block : blocks) {
            String type = block.path("type").asText("");
            int level = block.path("props").path("level").asInt(-1);
            if ("heading".equals(type) && level == 1) {
                continue;
            }
            String text = inlineText(block).trim();
            if (!text.isBlank()) {
                return summarize(text);
            }
        }
        return "";
    }

    private String inlineText(JsonNode block) {
        StringBuilder sb = new StringBuilder();
        JsonNode content = block.path("content");
        if (content.isArray()) {
            for (JsonNode inline : content) {
                sb.append(inline.path("text").asText(""));
            }
        }
        JsonNode children = block.path("children");
        if (children.isArray()) {
            for (JsonNode child : children) {
                String childText = inlineText(child);
                if (!childText.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append(' ');
                    }
                    sb.append(childText);
                }
            }
        }
        return sb.toString();
    }

    private ObjectNode headingBlock(String text, int level) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "heading");
        ObjectNode props = block.putObject("props");
        props.put("level", level);
        ArrayNode content = block.putArray("content");
        content.add(textNode(text));
        block.putArray("children");
        return block;
    }

    private ObjectNode paragraphBlock(String text) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "paragraph");
        ArrayNode content = block.putArray("content");
        if (!text.isBlank()) {
            content.add(textNode(text));
        }
        block.putArray("children");
        return block;
    }

    private ObjectNode bulletListItemBlock(String text) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "bulletListItem");
        block.putObject("props");
        ArrayNode content = block.putArray("content");
        content.add(textNode(text));
        block.putArray("children");
        return block;
    }

    private ObjectNode numberedListItemBlock(int start, String text) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "numberedListItem");
        ObjectNode props = block.putObject("props");
        props.put("start", start);
        ArrayNode content = block.putArray("content");
        content.add(textNode(text));
        block.putArray("children");
        return block;
    }

    private ObjectNode checkListItemBlock(boolean checked, String text) {
        ObjectNode block = objectMapper.createObjectNode();
        block.put("type", "checkListItem");
        ObjectNode props = block.putObject("props");
        props.put("checked", checked);
        ArrayNode content = block.putArray("content");
        content.add(textNode(text));
        block.putArray("children");
        return block;
    }

    private ObjectNode textNode(String text) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("type", "text");
        node.put("text", text);
        node.set("styles", objectMapper.createObjectNode());
        return node;
    }

    private List<String> splitMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        String[] parts = markdown.split("\\n\\s*\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private void appendMarkdownBlocks(ArrayNode blocks, String markdown) {
        for (String chunk : splitMarkdown(markdown)) {
            List<String> paragraphLines = new ArrayList<>();
            List<MarkdownListLine> listLines = new ArrayList<>();
            String activeListType = null;

            for (String line : splitLines(chunk)) {
                MarkdownListLine listLine = parseListLine(line);
                if (listLine == null) {
                    if (!listLines.isEmpty()) {
                        appendListLines(blocks, listLines);
                        listLines.clear();
                        activeListType = null;
                    }
                    paragraphLines.add(line);
                    continue;
                }

                if (!paragraphLines.isEmpty()) {
                    blocks.add(paragraphBlock(String.join("\n", paragraphLines)));
                    paragraphLines.clear();
                }
                if (activeListType != null && !activeListType.equals(listLine.blockType())) {
                    appendListLines(blocks, listLines);
                    listLines.clear();
                }
                activeListType = listLine.blockType();
                listLines.add(listLine);
            }

            if (!paragraphLines.isEmpty()) {
                blocks.add(paragraphBlock(String.join("\n", paragraphLines)));
            }
            if (!listLines.isEmpty()) {
                appendListLines(blocks, listLines);
            }
        }
    }

    private List<String> splitLines(String chunk) {
        String[] parts = chunk.split("\\r?\\n");
        List<String> lines = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private boolean isListBlockType(String blockType) {
        return "bulletListItem".equals(blockType)
            || "numberedListItem".equals(blockType)
            || "checkListItem".equals(blockType);
    }

    private String blockToMarkdownLine(JsonNode block) {
        String blockType = block.path("type").asText("");
        String text = inlineText(block).trim();
        if (text.isBlank()) {
            return "";
        }
        return switch (blockType) {
            case "bulletListItem" -> "- " + text;
            case "checkListItem" -> "- [" + (block.path("props").path("checked").asBoolean(false) ? "x" : " ") + "] " + text;
            case "numberedListItem" -> {
                int start = block.path("props").path("start").asInt(1);
                yield start + ". " + text;
            }
            default -> text;
        };
    }

    private MarkdownListLine parseListLine(String line) {
        Matcher checklistMatcher = CHECKLIST_ITEM.matcher(line);
        if (checklistMatcher.matches()) {
            return new MarkdownListLine("checkListItem", checklistMatcher.group(2).trim(), "x".equalsIgnoreCase(checklistMatcher.group(1)), null);
        }

        Matcher numberedMatcher = NUMBERED_ITEM.matcher(line);
        if (numberedMatcher.matches()) {
            return new MarkdownListLine("numberedListItem", numberedMatcher.group(2).trim(), false, Integer.parseInt(numberedMatcher.group(1)));
        }

        Matcher bulletMatcher = BULLET_ITEM.matcher(line);
        if (bulletMatcher.matches()) {
            return new MarkdownListLine("bulletListItem", bulletMatcher.group(1).trim(), false, null);
        }

        return null;
    }

    private void appendListLines(ArrayNode blocks, List<MarkdownListLine> listLines) {
        for (MarkdownListLine line : listLines) {
            switch (line.blockType()) {
                case "checkListItem" -> blocks.add(checkListItemBlock(line.checked(), line.text()));
                case "numberedListItem" -> blocks.add(numberedListItemBlock(line.start() == null ? 1 : line.start(), line.text()));
                default -> blocks.add(bulletListItemBlock(line.text()));
            }
        }
    }

    private String summarize(String markdown) {
        String normalized = markdown == null ? "" : markdown.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 160) {
            return normalized;
        }
        return normalized.substring(0, 157) + "...";
    }

    private String slugify(String label) {
        String normalized = label == null ? "" : label.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? "section" : normalized;
    }

    private String normalizeLabel(String label) {
        return label == null ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private record SectionCapture(String label, List<JsonNode> content) {
    }

    private record MarkdownListLine(String blockType, String text, boolean checked, Integer start) {
    }
}
