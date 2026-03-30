package com.notesapp.service.aiwrite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.config.AiProperties;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentMeta;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiResponsesAiWriteProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Queue<String> queuedResponses = new ArrayDeque<>();
    private final Queue<JsonNode> capturedRequests = new ArrayDeque<>();

    private HttpServer server;
    private OpenAiResponsesAiWriteProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", this::handleResponse);
        server.start();

        AiProperties properties = new AiProperties();
        properties.setProvider("openai");
        properties.setOpenAiApiKey("test-key");
        properties.setOpenAiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.setRouterModel("gpt-5-mini-2025-08-07");
        properties.setPlannerModel("gpt-5-mini-2025-08-07");
        properties.setSummaryModel("gpt-5-mini-2025-08-07");

        provider = new OpenAiResponsesAiWriteProvider(properties, new CanonicalNoteTemplates(), objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void routeBuildsStructuredRequestAndParsesResponse() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"generic_note/v1",
              "confidence":0.88,
              "reasonCodes":["selected_note_hint"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "Add this to the current note",
            2L,
            11L,
            List.of("Earlier context"),
            new RetrievalBundle(
                List.of(new NotebookCandidate(2L, "Work websites", "Work links and references", 0.82)),
                List.of(new NoteCandidate(11L, 2L, "Launch", "Launch note", "generic_note/v1", List.of("Summary", "Body"), "Current content", Instant.now(), 0.91, true))
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Launch", "Launch note", "generic_note/v1", "v1", 2L, 7L),
                List.of(new NoteSection("body", "Body", "body", "Current content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.WRITE_EXISTING_NOTE);
        assertThat(routePlan.targetNoteId()).isEqualTo(11L);
        JsonNode request = capturedRequests.remove();
        assertThat(request.path("model").asText()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.path("reasoning").path("effort").asText()).isEqualTo("minimal");
        assertThat(request.path("text").path("format").path("name").asText()).isEqualTo("noteszero_route_plan_v1");
    }

    @Test
    void routeCombinesStructuredOutputAcrossMultipleContentChunks() throws Exception {
        queueRawResponseBody("""
            {
              "id":"resp_test",
              "status":"completed",
              "output":[
                {
                  "type":"message",
                  "content":[
                    {
                      "type":"output_text",
                      "text":"{\\"intent\\":\\"WRITE_EXISTING_NOTE\\",\\"targetNotebookId\\":2,\\"targetNoteId\\":11,"
                    },
                    {
                      "type":"output_text",
                      "text":"\\"targetNoteType\\":\\"generic_note/v1\\",\\"confidence\\":0.88,\\"reasonCodes\\":[\\"selected_note_hint\\"],\\"strategy\\":\\"DIRECT_APPLY\\",\\"answer\\":null}"
                    }
                  ]
                }
              ]
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "Add this to the current note",
            2L,
            11L,
            List.of("Earlier context"),
            new RetrievalBundle(
                List.of(new NotebookCandidate(2L, "Work websites", "Work links and references", 0.82)),
                List.of(new NoteCandidate(11L, 2L, "Launch", "Launch note", "generic_note/v1", List.of("Summary", "Body"), "Current content", Instant.now(), 0.91, true))
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Launch", "Launch note", "generic_note/v1", "v1", 2L, 7L),
                List.of(new NoteSection("body", "Body", "body", "Current content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.WRITE_EXISTING_NOTE);
        assertThat(routePlan.targetNoteId()).isEqualTo(11L);
    }

    @Test
    void plannerForcesInboxSectionWhenRouteStrategyRequiresIt() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"generic_note/v1",
              "ops":[
                {
                  "op":"APPEND_SECTION_CONTENT",
                  "sectionId":"body",
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":"remembered item",
                  "contentMarkdown":"Remember to renew the domain.",
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchRequestContext context = new PatchRequestContext(
            "Remember to renew the domain.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                11L,
                "generic_note/v1",
                0.42,
                List.of("retrieval_note_match"),
                RouteStrategy.NOTE_INBOX,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Launch", "Launch note", "generic_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("summary", "Summary", "summary", "Launch summary"),
                    new NoteSection("body", "Body", "body", "Current content"),
                    new NoteSection("inbox", "Inbox", "inbox", "")
                )
            )
        );

        PatchPlanV1 patchPlan = provider.plan(context);

        assertThat(patchPlan.fallbackToInbox()).isTrue();
        assertThat(patchPlan.ops()).hasSize(1);
        assertThat(patchPlan.ops().getFirst().sectionId()).isEqualTo("inbox");
        JsonNode request = capturedRequests.remove();
        assertThat(request.path("reasoning").path("effort").asText()).isEqualTo("low");
        assertThat(request.path("text").path("format").path("name").asText()).isEqualTo("noteszero_patch_plan_v1");
    }

    @Test
    void summarizeBuildsStructuredRequestAndReturnsTrimmedSummary() throws Exception {
        queueOutput("""
            {
              "summaryShort":"Launch plan and rollout tasks for the NotesZero v2 write system.",
              "routingSummary":"Notes about the NotesZero product launch, rollout status, and implementation tasks."
            }
            """);

        NoteSummaryResult summary = provider.summarize(new NoteDocumentV1(
            new NoteDocumentMeta(11L, "Launch", "", "project_note/v1", "v1", 2L, 7L),
            List.of(new NoteSection("summary", "Summary", "summary", "Launch plan details"))
        ));

        assertThat(summary.summaryShort()).contains("Launch plan");
        JsonNode request = capturedRequests.remove();
        assertThat(request.path("reasoning").path("effort").asText()).isEqualTo("minimal");
        assertThat(request.path("text").path("format").path("name").asText()).isEqualTo("noteszero_note_summary_v1");
    }

    @Test
    void routeKeepsExplicitCreateRequestsOnCreatePath() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":3,
              "targetNoteId":17,
              "targetNoteType":"generic_note/v1",
              "confidence":0.94,
              "reasonCodes":["selected_note_hint","retrieval_note_match"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "Create a note called Sleep Regression Observations with a short summary.",
            3L,
            17L,
            List.of(),
            new RetrievalBundle(
                List.of(new NotebookCandidate(3L, "Family health", "Family health notes", 0.88)),
                List.of(new NoteCandidate(17L, 3L, "Sleep Regression Observations", "Existing note", "generic_note/v1", List.of("Summary", "Body"), "Existing content", Instant.now(), 0.93, true))
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(17L, "Existing Note", "Existing note", "generic_note/v1", "v1", 3L, 5L),
                List.of(new NoteSection("body", "Body", "body", "Current content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.CREATE_NOTE);
        assertThat(routePlan.targetNoteId()).isNull();
        assertThat(routePlan.strategy()).isEqualTo(RouteStrategy.DIRECT_APPLY);
    }

    @Test
    void routeMovesLowSpecificityAmbiguousCapturesToNotebookInbox() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "confidence":0.72,
              "reasonCodes":["selected_note_hint","ambiguous_capture"],
              "strategy":"NOTE_INBOX",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "Need to remember this at some point.",
            2L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(new NotebookCandidate(2L, "Work websites", "Work notes", 0.78)),
                List.of(new NoteCandidate(11L, 2L, "NotesZero Launch", "Launch note", "project_note/v1", List.of("Summary", "Tasks"), "Launch tasks", Instant.now(), 0.76, false))
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "NotesZero Launch", "Launch note", "project_note/v1", "v1", 2L, 7L),
                List.of(new NoteSection("tasks", "Tasks", "task_list", "- [ ] Existing task"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.CREATE_NOTE);
        assertThat(routePlan.targetNoteId()).isNull();
        assertThat(routePlan.strategy()).isEqualTo(RouteStrategy.NOTEBOOK_INBOX);
        assertThat(routePlan.targetNoteType()).isEqualTo(CanonicalNoteTemplates.GENERIC_NOTE);
    }

    @Test
    void routeMovesLowSpecificityDirectApplyCapturesToNotebookInbox() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "confidence":0.91,
              "reasonCodes":["selected_note_hint","explicit_capture"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "Need to remember this before the weekend.",
            2L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(new NotebookCandidate(2L, "Work websites", "Work notes", 0.78)),
                List.of(new NoteCandidate(11L, 2L, "NotesZero Launch", "Launch note", "project_note/v1", List.of("Summary", "Tasks"), "Launch tasks", Instant.now(), 0.76, false))
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "NotesZero Launch", "Launch note", "project_note/v1", "v1", 2L, 7L),
                List.of(new NoteSection("tasks", "Tasks", "task_list", "- [ ] Existing task"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.CREATE_NOTE);
        assertThat(routePlan.targetNoteId()).isNull();
        assertThat(routePlan.strategy()).isEqualTo(RouteStrategy.NOTEBOOK_INBOX);
    }

    @Test
    void routeUsesExplicitMentionedNoteAcrossNotebookBoundaries() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":3,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "confidence":0.72,
              "reasonCodes":["selected_note_hint"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "In Deploy Runbook, add a note to verify Flyway migration order before frontend deploy.",
            3L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(
                    new NotebookCandidate(2L, "Work websites", "Work notes", 0.84),
                    new NotebookCandidate(3L, "Dog notes", "Dog notes", 0.78)
                ),
                List.of(
                    new NoteCandidate(11L, 3L, "Dog Training Plan", "Dog plan", "project_note/v1", List.of("Status", "Tasks"), "Dog training status", Instant.now(), 0.88, false),
                    new NoteCandidate(21L, 2L, "Deploy Runbook", "Deployment notes", "generic_note/v1", List.of("Summary", "Body"), "Flyway migration order", Instant.now(), 0.86, true)
                )
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Dog Training Plan", "Dog plan", "project_note/v1", "v1", 3L, 5L),
                List.of(new NoteSection("status", "Status", "status", "Current content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.WRITE_EXISTING_NOTE);
        assertThat(routePlan.targetNotebookId()).isEqualTo(2L);
        assertThat(routePlan.targetNoteId()).isEqualTo(21L);
        assertThat(routePlan.targetNoteType()).isEqualTo("generic_note/v1");
    }

    @Test
    void routeRedirectsUnrelatedSelectedDogNoteToFamilyNotebookCapture() throws Exception {
        queueOutput("""
            {
              "intent":"WRITE_EXISTING_NOTE",
              "targetNotebookId":1,
              "targetNoteId":11,
              "targetNoteType":"generic_note/v1",
              "confidence":0.62,
              "reasonCodes":["selected_note_hint"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "my toddler pooped on floor",
            1L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(
                    new NotebookCandidate(3L, "Family health", "Baby and family health observations", 0.62),
                    new NotebookCandidate(1L, "Dog notes", "Dog health and behavior notes", 0.12)
                ),
                List.of(
                    new NoteCandidate(11L, 1L, "Dog note", "Dog note", "generic_note/v1", List.of("Summary", "Body"), "Dog content", Instant.now(), 0.18, false)
                )
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Dog note", "Dog note", "generic_note/v1", "v1", 1L, 5L),
                List.of(new NoteSection("body", "Body", "body", "Dog content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.CREATE_NOTE);
        assertThat(routePlan.targetNotebookId()).isEqualTo(3L);
        assertThat(routePlan.targetNoteId()).isNull();
        assertThat(routePlan.reasonCodes()).contains("notebook_match_override");
    }

    @Test
    void routePromotesConcreteNotebookInboxCaptureToExistingFamilyNote() throws Exception {
        queueOutput("""
            {
              "intent":"CREATE_NOTE",
              "targetNotebookId":3,
              "targetNoteId":null,
              "targetNoteType":"generic_note/v1",
              "confidence":0.85,
              "reasonCodes":["selected_notebook_hint","ambiguous_granularity"],
              "strategy":"NOTEBOOK_INBOX",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "my toddler pooped on floor",
            1L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(
                    new NotebookCandidate(3L, "Family health", "Baby and family health observations", 0.84),
                    new NotebookCandidate(1L, "Dog notes", "Dog health and behavior notes", 0.10)
                ),
                List.of(
                    new NoteCandidate(21L, 3L, "Family symptom log", "Family symptom tracking", "generic_note/v1", List.of("Summary", "Body"), "Symptom tracking", Instant.now(), 0.58, false),
                    new NoteCandidate(11L, 1L, "Dog note", "Dog note", "generic_note/v1", List.of("Summary", "Body"), "Dog content", Instant.now(), 0.18, false)
                )
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Dog note", "Dog note", "generic_note/v1", "v1", 1L, 5L),
                List.of(new NoteSection("body", "Body", "body", "Dog content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.WRITE_EXISTING_NOTE);
        assertThat(routePlan.targetNotebookId()).isEqualTo(3L);
        assertThat(routePlan.targetNoteId()).isEqualTo(21L);
        assertThat(routePlan.strategy()).isEqualTo(RouteStrategy.DIRECT_APPLY);
        assertThat(routePlan.reasonCodes()).contains("concrete_capture", "retrieval_note_match");
    }

    @Test
    void routeRedirectsWrongSelectedNotebookForFamilyCapture() throws Exception {
        queueOutput("""
            {
              "intent":"CREATE_NOTE",
              "targetNotebookId":1,
              "targetNoteId":null,
              "targetNoteType":"generic_note/v1",
              "confidence":0.62,
              "reasonCodes":["selected_notebook_hint"],
              "strategy":"DIRECT_APPLY",
              "answer":null
            }
            """);

        RouteRequestContext context = new RouteRequestContext(
            "my toddler pooped on floor",
            1L,
            11L,
            List.of(),
            new RetrievalBundle(
                List.of(
                    new NotebookCandidate(3L, "Family health", "Baby and family health observations", 0.62),
                    new NotebookCandidate(1L, "Dog notes", "Dog health and behavior notes", 0.12)
                ),
                List.of(
                    new NoteCandidate(11L, 1L, "Dog note", "Dog note", "generic_note/v1", List.of("Summary", "Body"), "Dog content", Instant.now(), 0.18, false)
                )
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Dog note", "Dog note", "generic_note/v1", "v1", 1L, 5L),
                List.of(new NoteSection("body", "Body", "body", "Dog content"))
            )
        );

        RoutePlanV1 routePlan = provider.route(context);

        assertThat(routePlan.intent()).isEqualTo(RouteIntent.CREATE_NOTE);
        assertThat(routePlan.targetNotebookId()).isEqualTo(3L);
        assertThat(routePlan.targetNoteId()).isNull();
        assertThat(routePlan.reasonCodes()).contains("notebook_match_override");
    }

    @Test
    void plannerMovesGenericAddNoteRequestsBackToBody() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":21,
              "targetNoteType":"generic_note/v1",
              "ops":[
                {
                  "op":"APPEND_SECTION_CONTENT",
                  "sectionId":"action_items",
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":null,
                  "contentMarkdown":"Verify Flyway migration order before frontend deploy.",
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "In Deploy Runbook, add a note to verify Flyway migration order before frontend deploy.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                21L,
                "generic_note/v1",
                0.88,
                List.of("explicit_note_title_match"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(21L, "Deploy Runbook", "Deployment notes", "generic_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("summary", "Summary", "summary", "Deployment notes"),
                    new NoteSection("body", "Body", "body", "Existing content"),
                    new NoteSection("action_items", "Action Items", "task_list", "")
                )
            )
        ));

        assertThat(patchPlan.ops()).hasSize(1);
        assertThat(patchPlan.ops().getFirst().sectionId()).isEqualTo("body");
    }

    @Test
    void plannerSynthesizesFallbackOperationWhenModelReturnsNoOps() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "ops":[],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "Decision: preserve quoted phrases and exact measurements.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                11L,
                "project_note/v1",
                0.86,
                List.of("selected_note_hint"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Extended Eval Stream", "Working note", "project_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("status", "Status", "status", "Current status"),
                    new NoteSection("decisions", "Decisions", "dated_log", "- Existing decision"),
                    new NoteSection("tasks", "Tasks", "task_list", "- [ ] Existing task")
                )
            )
        ));

        assertThat(patchPlan.ops()).hasSize(1);
        assertThat(patchPlan.ops().getFirst().sectionId()).isEqualTo("decisions");
        assertThat(patchPlan.ops().getFirst().contentMarkdown()).contains("preserve quoted phrases and exact measurements");
    }

    @Test
    void plannerRepairsVisibleSummaryWhenModelOnlyUpdatesMetadata() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "ops":[
                {
                  "op":"UPDATE_NOTE_SUMMARY",
                  "sectionId":null,
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":"Tracks local eval work.",
                  "contentMarkdown":null,
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "Add a short summary that this note tracks local eval work.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                11L,
                "project_note/v1",
                0.9,
                List.of("selected_note_hint"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Extended Eval Stream", "Working note", "project_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("summary", "Summary", "summary", "Working note for iterative AI write testing."),
                    new NoteSection("status", "Status", "status", "Current status")
                )
            )
        ));

        assertThat(patchPlan.ops()).hasSize(2);
        assertThat(patchPlan.ops()).anySatisfy(operation -> {
            assertThat(operation.op()).isEqualTo(PatchOpType.UPDATE_NOTE_SUMMARY);
            assertThat(operation.summaryShort()).isEqualTo("Tracks local eval work.");
        });
        assertThat(patchPlan.ops()).anySatisfy(operation -> {
            assertThat(operation.op()).isEqualTo(PatchOpType.REPLACE_SECTION_CONTENT);
            assertThat(operation.sectionId()).isEqualTo("summary");
            assertThat(operation.contentMarkdown()).isEqualTo("Tracks local eval work.");
        });
    }

    @Test
    void plannerRestoresProtectedModelIdsWhenPlannerDropsThem() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "ops":[
                {
                  "op":"APPEND_SECTION_CONTENT",
                  "sectionId":"tasks",
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":null,
                  "contentMarkdown":"- [ ] Compare the models in evals.",
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "Add a task to compare gpt-5-mini-2025-08-07 and gpt-5.4-2026-03-05 in evals.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                11L,
                "project_note/v1",
                0.92,
                List.of("selected_note_hint"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Extended Eval Stream", "Working note", "project_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("tasks", "Tasks", "task_list", "- [ ] Existing task"),
                    new NoteSection("status", "Status", "status", "Current status")
                )
            )
        ));

        assertThat(patchPlan.ops()).hasSize(1);
        assertThat(patchPlan.ops().getFirst().contentMarkdown()).contains("gpt-5-mini-2025-08-07");
        assertThat(patchPlan.ops().getFirst().contentMarkdown()).contains("gpt-5.4-2026-03-05");
    }

    @Test
    void plannerDropsDuplicateAppendWhenSameTaskAlreadyExists() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":11,
              "targetNoteType":"project_note/v1",
              "ops":[
                {
                  "op":"APPEND_SECTION_CONTENT",
                  "sectionId":"tasks",
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":null,
                  "contentMarkdown":"- [ ] Watch undo-rate spikes after deploy.",
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "Add a task to watch undo-rate spikes after deploy.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                11L,
                "project_note/v1",
                0.88,
                List.of("selected_note_hint"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(11L, "Extended Eval Stream", "Working note", "project_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("tasks", "Tasks", "task_list", "- [ ] Watch undo-rate spikes after deploy."),
                    new NoteSection("status", "Status", "status", "Current status")
                )
            )
        ));

        assertThat(patchPlan.ops()).isEmpty();
    }

    @Test
    void plannerRestoresExactCommandWhenPlannerDropsIt() throws Exception {
        queueOutput("""
            {
              "targetNotebookId":2,
              "targetNoteId":21,
              "targetNoteType":"generic_note/v1",
              "ops":[
                {
                  "op":"APPEND_SECTION_CONTENT",
                  "sectionId":"body",
                  "afterSectionId":null,
                  "title":null,
                  "summaryShort":null,
                  "contentMarkdown":"Save the deploy command for staging.",
                  "sections":null
                }
              ],
              "fallbackToInbox":false,
              "plannerPromptVersion":"model-generated"
            }
            """);

        PatchPlanV1 patchPlan = provider.plan(new PatchRequestContext(
            "Save the exact command: ./deploy --env staging-w01 --dry-run.",
            new RoutePlanV1(
                RouteIntent.WRITE_EXISTING_NOTE,
                2L,
                21L,
                "generic_note/v1",
                0.9,
                List.of("selected_note_hint"),
                RouteStrategy.DIRECT_APPLY,
                null
            ),
            new NoteDocumentV1(
                new NoteDocumentMeta(21L, "Deploy Runbook", "Deployment notes", "generic_note/v1", "v1", 2L, 7L),
                List.of(
                    new NoteSection("summary", "Summary", "summary", "Deployment notes"),
                    new NoteSection("body", "Body", "body", "Existing content")
                )
            )
        ));

        assertThat(patchPlan.ops()).hasSize(1);
        assertThat(patchPlan.ops().getFirst().contentMarkdown()).contains("./deploy --env staging-w01 --dry-run");
    }

    private void queueOutput(String json) throws Exception {
        String compactJson = objectMapper.readTree(json).toString();
        queuedResponses.add("""
            {
              "id":"resp_test",
              "output_text":%s
            }
            """.formatted(objectMapper.writeValueAsString(compactJson)));
    }

    private void queueRawResponseBody(String json) {
        queuedResponses.add(json);
    }

    private void handleResponse(HttpExchange exchange) throws IOException {
        try (exchange; InputStream inputStream = exchange.getRequestBody()) {
            capturedRequests.add(objectMapper.readTree(inputStream.readAllBytes()));
            byte[] responseBytes = queuedResponses.remove().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        } catch (Exception ex) {
            byte[] responseBytes = ("{\"error\":{\"message\":\"" + ex.getMessage() + "\"}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
        }
    }
}
