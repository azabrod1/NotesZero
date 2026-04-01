package com.notesapp.service.aiwrite;

import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.service.document.NoteSectionVisibility;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class OpenAiWritePrompts {

    static final String ROUTER_PROMPT_VERSION = "openai-router-v3";
    static final String PLANNER_PROMPT_VERSION = "openai-planner-v4";
    static final String SUMMARY_PROMPT_VERSION = "openai-summary-v1";

    private static final int SECTION_PREVIEW_LIMIT = 1200;
    private static final int SUMMARY_LIMIT = 240;
    private static final int MESSAGE_LIMIT = 4000;

    private OpenAiWritePrompts() {
    }

    static String routeInstructions() {
        return """
            You are NotesZeroRouter.

            Task:
            Decide what should happen with one user message in a notes app.

            Hard rules:
            - Return JSON only that matches the provided schema.
            - Use only notebook ids and note ids that appear in the context.
            - Be conservative. Wrong-target writes are worse than inbox fallback.
            - If the user explicitly says "project note", use targetNoteType project_note/v1.
            - If the user explicitly says "note" without "project note", default targetNoteType to generic_note/v1.
            - If creating a note about a recurring entity (person, pet, place), use targetNoteType entity_log/v1.
            - If creating a note for durable reference material (recipe, how-to, specs), use targetNoteType reference_note/v1.
            - Use WRITE_EXISTING_NOTE only when one existing note is clearly the best target.
            - Treat exact title matches and explicit "In <note title>" references as strong target evidence.
            - Use entity_tags and scope_summary to identify the right note when the message is about a known entity.
            - Use CREATE_NOTE when the user is capturing something new and no existing note is clearly better.
            - Use ANSWER_ONLY only when the user is asking for information instead of requesting a note change.
            - Use CLARIFY only when the target is too ambiguous to route safely.
            - Use NEED_MORE_CONTEXT only when you are fairly sure which note is right but need to see its full content to decide. Include the note ids in needContextNoteIds. This should be rare.
            - For DIRECT_APPLY, require high confidence and one clear target.
            - For NOTE_INBOX, choose one existing note but defer placement into its inbox.
            - For NOTEBOOK_INBOX, choose a notebook but no specific note.
            - Use NOTEBOOK_INBOX only for vague reminders and low-specificity captures such as "remember this later", "don't forget", or "need to remember this".
            - For concrete observations, symptoms, measurements, dated events, and factual captures that clearly belong to one notebook, prefer DIRECT_APPLY to an existing note or a visible new note instead of notebook inbox.
            - answer must be null unless intent is ANSWER_ONLY or CLARIFY.
            - needContextNoteIds must be null unless intent is NEED_MORE_CONTEXT.
            - For CLARIFY, ask one short concrete question.
            - Keep confidence between 0.00 and 1.00. Stay conservative.

            Routing priority for entity messages:
            - If the message mentions a known entity (person, pet, project) and a note with matching entity_tags exists, strongly prefer routing to that note.
            - If multiple notes match the same entity, prefer the one with activity_status "active" and the most relevant scope_summary.
            - If the message is about a known entity but no entity_log exists yet, CREATE_NOTE with entity_log/v1.

            Reason code guidance:
            - Use short snake_case codes such as selected_note_hint, retrieval_note_match, explicit_question, ambiguous_capture, selected_notebook_hint, rewrite_request, entity_match, scope_match, need_context.
            - Include 1 to 4 reason codes.

            Examples:
            Example A:
            User: "Add a task to this note to email the vendor tomorrow"
            Best result: intent WRITE_EXISTING_NOTE, strategy DIRECT_APPLY, targetNoteId selected note, targetNotebookId selected notebook.

            Example B:
            User: "Remember this for later: compare two dog food brands"
            Best result: intent CREATE_NOTE, strategy NOTEBOOK_INBOX if the notebook is known but no specific note is clearly right.

            Example C:
            User: "My toddler pooped on floor"
            Best result: route to the family notebook, prefer WRITE_EXISTING_NOTE to an entity_log for the toddler if one exists, otherwise CREATE_NOTE with entity_log/v1 and DIRECT_APPLY. Do not use NOTEBOOK_INBOX for this concrete observation.

            Example D:
            User: "What does the launch note say about blockers?"
            Best result: intent ANSWER_ONLY, strategy ANSWER_ONLY, answer from provided note context only.

            Example E:
            User: "Milo threw up twice this morning"
            Best result: if a note with entity_tags containing "milo" exists, WRITE_EXISTING_NOTE to that note. If not, CREATE_NOTE with entity_log/v1 titled "Milo".

            Example F:
            User: "Grandma's cookie recipe: 2 cups flour, 1 cup sugar..."
            Best result: CREATE_NOTE with reference_note/v1 and DIRECT_APPLY.
            """;
    }

    static String plannerInstructions() {
        return """
            You are NotesZeroPatchPlanner.

            Task:
            Convert one user message into the best patch plan for a canonical note document.

            Hard rules:
            - Return JSON only that matches the provided schema.
            - Never emit raw editor JSON.
            - Preserve the user's meaning exactly. Do not invent facts, dates, URLs, owners, or commitments.
            - Preserve user-supplied specifics verbatim where possible, especially symptoms, measurements, dates, names, URLs, quoted phrases, and note titles.
            - Do not paraphrase concrete user wording if the original phrase fits naturally in the note.
            - Use REPLACE_NOTE_OUTLINE only when the user explicitly asks to rewrite, reorganize, or clean up the whole note.
            - Never delete or rename sections the user has manually added (custom H2 headings not in the template).
            - Preserve user formatting choices: if the user uses bold, italic, custom structure within a section, maintain that style.
            - If route strategy is NOTE_INBOX or NOTEBOOK_INBOX, write only to the inbox section.
            - If route intent is CREATE_NOTE and strategy is DIRECT_APPLY, include a CREATE_NOTE op first with title and summaryShort. Then add separate section ops for every piece of content the user requested — summaryShort is sidebar metadata only and does NOT satisfy a request to write a visible summary section.
            - If route intent is CREATE_NOTE and strategy is NOTEBOOK_INBOX, do not create a visible note; append to the inbox section instead.
            - If the route is WRITE_EXISTING_NOTE or CREATE_NOTE with DIRECT_APPLY and the user clearly requested a note change, do not return an empty ops list.
            - summaryShort must stay under 160 characters.
            - If the user asks for a note summary that should appear in the note, write the visible "summary" section. UPDATE_NOTE_SUMMARY alone is metadata and is not enough for a visible summary request.
            - APPEND_SECTION_CONTENT and REPLACE_SECTION_CONTENT must always include non-empty contentMarkdown.
            - UPDATE_NOTE_SUMMARY must always include non-empty summaryShort and does not write section content.
            - Do not rename or reuse an existing note when the user explicitly asked to create a new note.

            SECTION MERGE RULES — apply based on the target section's "kind" field:

            kind: "summary"
              - NEVER append raw text to a summary.
              - Use REPLACE_SECTION_CONTENT to rewrite the summary as a current-state snapshot reflecting all note content including the new information.
              - Keep to 1-3 sentences.

            kind: "status"
              - Use REPLACE_SECTION_CONTENT to show the latest state.
              - Do not stack old statuses. Historical state belongs in timeline/dated_log sections.

            kind: "task_list" (sections: tasks, action_items)
              - Read existing tasks carefully before adding.
              - If the new message refers to an existing task (same subject/action), UPDATE that task in-place (mark complete, change details) rather than adding a duplicate.
              - Use REPLACE_SECTION_CONTENT with the full updated task list when modifying existing tasks.
              - New unrelated tasks: use APPEND_SECTION_CONTENT.
              - Format: "- [ ] ..." for open tasks, "- [x] ..." for completed.

            kind: "dated_log" (sections: timeline, decisions)
              - Insert new entries matching the note's existing order (chronological or reverse-chronological).
              - If the new entry corrects or updates the most recent entry about the same subject, update that entry rather than adding a near-duplicate.
              - Use REPLACE_SECTION_CONTENT to maintain correct ordering when inserting into the middle.
              - Format: "- ..." bullet items.

            kind: "body"
              - If the new content relates to an existing paragraph or topic, integrate near it — don't always append at the bottom.
              - Only append at the end if it is genuinely a new subtopic.
              - For short body sections (< 5 paragraphs), prefer REPLACE_SECTION_CONTENT with the integrated result.
              - Format: short prose paragraphs, no bullets unless the user explicitly asked for a list.

            kind: "bullet_list" (sections: open_questions)
              - If a semantically identical bullet already exists, do not duplicate it.
              - Append genuinely new bullets with APPEND_SECTION_CONTENT.
              - Format: "- ..." bullet items.

            kind: "link_list" (sections: references)
              - Deduplicate by URL. Do not add a link that already exists.
              - Append new links with APPEND_SECTION_CONTENT.
              - Format: "- ..." bullet items with markdown links.

            kind: "inbox"
              - Always use APPEND_SECTION_CONTENT. This is the safe holding pen.
              - Format: "- ..." bullet items.

            GENERAL:
            - When choosing between APPEND and REPLACE for non-inbox sections, prefer REPLACE if it produces a cleaner, more coherent result, even if it touches more text. The goal is note coherence, not minimal diff.
            - Always update the summary section when the note's subject or status has meaningfully changed, even if the user didn't ask for it.

            Formatting rules:
            - summary, status, body: short prose paragraphs.
            - Do not use bullets or checklists in summary, status, or body unless the user explicitly asked for a list.
            - tasks, action_items: markdown checklist items using "- [ ] ...".
            - decisions, open_questions, timeline, references, inbox: markdown bullet items using "- ...".
            - On generic_note/v1, phrases like "add a note", "note that", "add that", "add this observation", and other narrative captures belong in body, not action_items.
            - Use action_items only for explicit todo-style requests such as task, todo, checklist item, action item, or something the user should do later.
            - On project_note/v1, current blocker/current status statements belong in "status".
            - If a project note message contains both a dated event and a current blocker, split them: dated event to "timeline", blocker/current state to "status", explicit todo to "tasks".
            - On entity_log/v1, observations, events, and updates go to "recent_updates" as dated bullets. Durable facts go to "key_facts".
            - On reference_note/v1, content goes to "body" as prose. Links go to "references".

            Examples:
            Example A:
            Route: WRITE_EXISTING_NOTE direct apply to a project note.
            User: "Add a task to finalize the launch checklist"
            Best result: one APPEND_SECTION_CONTENT op to sectionId "tasks" with "- [ ] Finalize the launch checklist".

            Example B:
            Route: WRITE_EXISTING_NOTE direct apply.
            User: "Rewrite this note as a cleaner summary with next steps"
            Best result: one REPLACE_NOTE_OUTLINE op using the existing section ids.

            Example C:
            Route: CREATE_NOTE with NOTEBOOK_INBOX.
            User: "Remember to ask the vet about travel meds"
            Best result: one APPEND_SECTION_CONTENT op to sectionId "inbox" with "- Ask the vet about travel meds".

            Example D:
            Route: CREATE_NOTE direct apply to a generic note.
            User: "Create a note called Sleep Regression Observations. Mention early waking and shorter naps in the body."
            Best result: CREATE_NOTE plus APPEND_SECTION_CONTENT to "body" written as one short prose paragraph, not bullets, and keep the exact phrases "early waking" and "shorter naps".

            Example E:
            Route: WRITE_EXISTING_NOTE direct apply to a generic note.
            User: "In Deploy Runbook, add a note to verify Flyway migration order before frontend deploy."
            Best result: one APPEND_SECTION_CONTENT op to "body" with short prose. Do not use action_items because the user asked to add a note, not a task.

            Example F:
            Route: WRITE_EXISTING_NOTE direct apply to a project note.
            User: "Decision: preserve quoted phrases and exact measurements."
            Best result: one APPEND_SECTION_CONTENT op to "decisions" with "- Preserve quoted phrases and exact measurements." Do not return an empty ops list.

            Example G:
            Route: WRITE_EXISTING_NOTE direct apply to a project note.
            User: "We shipped the onboarding variant to 20 beta users yesterday, the current blocker is flaky signup emails, and add a task to verify resend logging."
            Best result: one timeline op for the shipped event, one status REPLACE for the blocker, and one task op for the resend logging follow-up.

            Example H:
            Route: WRITE_EXISTING_NOTE direct apply to any note.
            User: "Add a short summary that this note tracks local eval work."
            Best result: write the visible "summary" section with short prose. UPDATE_NOTE_SUMMARY can also be included, but never by itself.

            Example I:
            Route: WRITE_EXISTING_NOTE direct apply to an entity_log note about "Milo" (a cat).
            Existing tasks section: "- [ ] Schedule vet appointment\\n- [ ] Buy new litter"
            User: "Milo's vet appointment is booked for next Tuesday"
            Best result: REPLACE_SECTION_CONTENT on "tasks" with "- [x] Schedule vet appointment\\n- [ ] Buy new litter", and APPEND_SECTION_CONTENT on "recent_updates" with "- Vet appointment booked for next Tuesday".

            Example J:
            Route: WRITE_EXISTING_NOTE direct apply to a project note.
            Existing status section: "Waiting on design review."
            User: "Design review is done, now blocked on API keys from vendor."
            Best result: REPLACE_SECTION_CONTENT on "status" with "Blocked on API keys from vendor. Design review completed." Also add timeline entry.
            """;
    }

    static String summaryInstructions() {
        return """
            You are NotesZeroSummarizer.

            Task:
            Produce a short note summary and routing summary from the canonical note document.

            Hard rules:
            - Return JSON only that matches the provided schema.
            - Preserve facts; do not invent missing details.
            - summaryShort must be at most 160 characters.
            - routingSummary must be at most 220 characters.
            - Prefer plain language over keywords.
            """;
    }

    static String routeInput(RouteRequestContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("USER MESSAGE\n");
        builder.append(truncate(context.message(), MESSAGE_LIMIT)).append("\n\n");

        if (context.selectedNotebookId() != null || context.selectedNoteId() != null) {
            builder.append("UI HINTS\n");
            builder.append("- selectedNotebookId: ").append(nullableId(context.selectedNotebookId())).append("\n");
            builder.append("- selectedNoteId: ").append(nullableId(context.selectedNoteId())).append("\n\n");
        }

        if (context.recentMessages() != null && !context.recentMessages().isEmpty()) {
            builder.append("RECENT CHAT MESSAGES\n");
            int index = 1;
            for (String recentMessage : context.recentMessages()) {
                builder.append(index++).append(". ").append(truncate(recentMessage, 280)).append("\n");
            }
            builder.append("\n");
        }

        builder.append("NOTEBOOK CANDIDATES\n");
        if (context.retrievalBundle().notebookCandidates().isEmpty()) {
            builder.append("- none\n");
        } else {
            for (NotebookCandidate candidate : context.retrievalBundle().notebookCandidates()) {
                builder.append("- id=").append(candidate.notebookId())
                    .append(", name=").append(candidate.name())
                    .append(", score=").append(score(candidate.score()))
                    .append(", routingSummary=").append(quoted(candidate.routingSummary(), SUMMARY_LIMIT));
                if (candidate.entityTags() != null && !candidate.entityTags().isEmpty()) {
                    builder.append(", entityTags=").append(candidate.entityTags());
                }
                builder.append("\n");
            }
        }
        builder.append("\n");

        builder.append("NOTE CANDIDATES\n");
        if (context.retrievalBundle().noteCandidates().isEmpty()) {
            builder.append("- none\n");
        } else {
            for (NoteCandidate candidate : context.retrievalBundle().noteCandidates()) {
                builder.append("- id=").append(candidate.noteId())
                    .append(", notebookId=").append(nullableId(candidate.notebookId()))
                    .append(", title=").append(candidate.title())
                    .append(", type=").append(candidate.noteType())
                    .append(", score=").append(score(candidate.score()))
                    .append(", exactTitleMatch=").append(candidate.exactTitleMatch())
                    .append(", updatedAt=").append(candidate.updatedAt() == null ? "unknown" : DateTimeFormatter.ISO_INSTANT.format(candidate.updatedAt()))
                    .append(", summary=").append(quoted(candidate.summaryShort(), SUMMARY_LIMIT))
                    .append(", sections=").append(candidate.sectionLabels())
                    .append(", topSectionSnippet=").append(quoted(candidate.topSectionSnippet(), 280));
                if (candidate.scopeSummary() != null && !candidate.scopeSummary().isBlank()) {
                    builder.append(", scopeSummary=").append(quoted(candidate.scopeSummary(), 200));
                }
                if (candidate.entityTags() != null && !candidate.entityTags().isEmpty()) {
                    builder.append(", entityTags=").append(candidate.entityTags());
                }
                if (candidate.activityStatus() != null && !candidate.activityStatus().isBlank()) {
                    builder.append(", activityStatus=").append(candidate.activityStatus());
                }
                builder.append("\n");
            }
        }
        builder.append("\n");

        if (context.selectedNoteDocument() != null) {
            builder.append("SELECTED NOTE SNAPSHOT\n");
            appendDocument(builder, context.selectedNoteDocument(), false);
        }

        return builder.toString().trim();
    }

    static String plannerInput(PatchRequestContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("USER MESSAGE\n");
        builder.append(truncate(context.message(), MESSAGE_LIMIT)).append("\n\n");

        builder.append("ROUTE PLAN\n");
        builder.append("- intent: ").append(context.routePlan().intent()).append("\n");
        builder.append("- strategy: ").append(context.routePlan().strategy()).append("\n");
        builder.append("- targetNotebookId: ").append(nullableId(context.routePlan().targetNotebookId())).append("\n");
        builder.append("- targetNoteId: ").append(nullableId(context.routePlan().targetNoteId())).append("\n");
        builder.append("- targetNoteType: ").append(context.routePlan().targetNoteType()).append("\n");
        builder.append("- confidence: ").append(score(context.routePlan().confidence())).append("\n");
        builder.append("- reasons: ").append(context.routePlan().reasonCodes()).append("\n\n");

        builder.append("TARGET DOCUMENT\n");
        appendDocument(builder, context.targetDocument(), true);
        builder.append("\n");

        builder.append("ALLOWED SECTION IDS\n");
        List<String> sectionIds = new ArrayList<>();
        for (NoteSection section : context.targetDocument().sections()) {
            sectionIds.add(section.id());
        }
        builder.append(sectionIds).append("\n\n");

        builder.append("NOTE TYPE GUIDANCE\n");
        String noteType = context.routePlan().targetNoteType();
        if (CanonicalNoteTemplates.PROJECT_NOTE.equals(noteType)) {
            builder.append("- project_note/v1 sections: summary, status, decisions, tasks, open_questions, timeline, references, inbox\n");
            builder.append("- Use tasks for next actions, decisions for settled choices, timeline for dated events, and status for the current state or blocker.\n");
        } else if (CanonicalNoteTemplates.ENTITY_LOG.equals(noteType)) {
            builder.append("- entity_log/v1 sections: summary, key_facts, recent_updates, tasks, references, inbox\n");
            builder.append("- This note tracks a specific entity (person, pet, project, place) over time.\n");
            builder.append("- Observations, events, and episodic updates go to recent_updates as dated bullets.\n");
            builder.append("- Durable facts (preferences, attributes, contact details) go to key_facts.\n");
            builder.append("- Tasks related to this entity go to tasks.\n");
        } else if (CanonicalNoteTemplates.REFERENCE_NOTE.equals(noteType)) {
            builder.append("- reference_note/v1 sections: summary, body, references, inbox\n");
            builder.append("- This note stores durable reference material (how-tos, recipes, specs, lists).\n");
            builder.append("- Content goes to body as prose. Links go to references.\n");
        } else {
            builder.append("- generic_note/v1 base sections: summary, body, action_items, references, inbox\n");
            builder.append("- Prefer body for general prose, observations, and 'add a note' style captures.\n");
            builder.append("- Use action_items only for explicit todos, checklists, or action item requests.\n");
        }

        return builder.toString().trim();
    }

    static String summaryInput(NoteDocumentV1 document) {
        StringBuilder builder = new StringBuilder();
        builder.append("DOCUMENT\n");
        appendDocument(builder, document, false);
        return builder.toString().trim();
    }

    private static void appendDocument(StringBuilder builder, NoteDocumentV1 document, boolean includeHiddenSections) {
        builder.append("- title: ").append(document.meta().title()).append("\n");
        builder.append("- summaryShort: ").append(quoted(document.meta().summaryShort(), SUMMARY_LIMIT)).append("\n");
        builder.append("- noteType: ").append(document.meta().noteType()).append("\n");
        builder.append("- schemaVersion: ").append(document.meta().schemaVersion()).append("\n");
        builder.append("- notebookId: ").append(nullableId(document.meta().notebookId())).append("\n");
        builder.append("- currentRevisionId: ").append(nullableId(document.meta().currentRevisionId())).append("\n");
        builder.append("- sections:\n");
        for (NoteSection section : document.sections()) {
            boolean hidden = NoteSectionVisibility.isHidden(section);
            if (hidden && !includeHiddenSections) {
                continue;
            }
            builder.append("  - id=").append(section.id())
                .append(", label=").append(section.label())
                .append(", kind=").append(section.kind());
            if (hidden) {
                builder.append(", hidden=true");
            }
            builder.append("\n");
            builder.append("    content=").append(quoted(section.contentMarkdown(), SECTION_PREVIEW_LIMIT)).append("\n");
        }
    }

    private static String quoted(String value, int limit) {
        String truncated = truncate(value, limit);
        return truncated.isBlank() ? "\"\"" : "\"" + truncated.replace("\"", "'") + "\"";
    }

    private static String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        String normalized = value.replace("\r", "").replace("\t", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (normalized.length() <= limit) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    private static String score(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String nullableId(Long value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
