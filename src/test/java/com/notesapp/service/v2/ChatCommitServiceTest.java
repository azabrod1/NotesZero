package com.notesapp.service.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.domain.ChatEvent;
import com.notesapp.repository.ChatEventRepository;
import com.notesapp.repository.NoteRepository;
import com.notesapp.repository.NotebookRepository;
import com.notesapp.config.AiProperties;
import com.notesapp.service.aiwrite.AiWriteProvider;
import com.notesapp.service.aiwrite.AiWriteProviderSelector;
import com.notesapp.service.aiwrite.NanoTriageResult;
import com.notesapp.service.aiwrite.DeterministicRetrievalService;
import com.notesapp.service.aiwrite.NoteCandidate;
import com.notesapp.service.aiwrite.NotebookCandidate;
import com.notesapp.service.aiwrite.RouteDecision;
import com.notesapp.service.aiwrite.RetrievalBundle;
import com.notesapp.service.aiwrite.RouteIntent;
import com.notesapp.service.aiwrite.RoutePlanV1;
import com.notesapp.service.aiwrite.RouteStrategy;
import com.notesapp.service.document.CanonicalNoteTemplates;
import com.notesapp.service.document.NoteDocumentMeta;
import com.notesapp.service.document.NoteDocumentV1;
import com.notesapp.service.document.NoteSection;
import com.notesapp.web.dto.v2.CommitChatRequest;
import com.notesapp.web.dto.v2.CommitChatResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatCommitServiceTest {

    private final ChatCommitService chatCommitService = new ChatCommitService(
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );

    @Test
    void expectedRevisionIdAppliesOnlyToWritesOnTheSelectedNote() {
        CommitChatRequest request = new CommitChatRequest();
        request.setSelectedNoteId(7L);
        request.setCurrentRevisionId(42L);

        RoutePlanV1 sameNoteWrite = new RoutePlanV1(
            RouteIntent.WRITE_EXISTING_NOTE,
            2L,
            7L,
            "generic_note/v1",
            0.9,
            List.of("selected_note_hint"),
            RouteStrategy.DIRECT_APPLY,
            null
        );
        RoutePlanV1 differentNoteWrite = new RoutePlanV1(
            RouteIntent.WRITE_EXISTING_NOTE,
            2L,
            9L,
            "generic_note/v1",
            0.9,
            List.of("retrieval_note_match"),
            RouteStrategy.DIRECT_APPLY,
            null
        );
        RoutePlanV1 createNote = new RoutePlanV1(
            RouteIntent.CREATE_NOTE,
            2L,
            null,
            "generic_note/v1",
            0.9,
            List.of("selected_notebook_hint"),
            RouteStrategy.DIRECT_APPLY,
            null
        );

        assertThat(chatCommitService.expectedRevisionIdForRoute(request, sameNoteWrite)).isEqualTo(42L);
        assertThat(chatCommitService.expectedRevisionIdForRoute(request, differentNoteWrite)).isNull();
        assertThat(chatCommitService.expectedRevisionIdForRoute(request, createNote)).isNull();
    }

    @Test
    void answerOnlyLoadsTargetNoteDocumentWhenModelAnswerIsBlank() {
        ChatEventRepository chatEventRepository = mock(ChatEventRepository.class);
        when(chatEventRepository.save(any(ChatEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeterministicRetrievalService retrievalService = mock(DeterministicRetrievalService.class);
        when(retrievalService.retrieve(any(String.class), any(), any())).thenReturn(new RetrievalBundle(
            List.of(new NotebookCandidate(2L, "Work websites", "Work notes", 0.84)),
            List.of(new NoteCandidate(21L, 2L, "Deploy Runbook", "Deployment notes", "generic_note/v1", List.of("Summary", "Body"), "Rollback uses the previous green build.", Instant.now(), 0.92, true))
        ));

        AiProperties aiProperties = mock(AiProperties.class);
        when(aiProperties.getRetrievalMode()).thenReturn("deterministic");

        AiWriteProvider provider = mock(AiWriteProvider.class);
        when(provider.triage(any(String.class), anyList())).thenReturn(new NanoTriageResult(NanoTriageResult.TriageType.WRITE, null));
        when(provider.routeWithTrace(any())).thenReturn(new RouteDecision(
            new RoutePlanV1(
                RouteIntent.ANSWER_ONLY,
                2L,
                21L,
                "generic_note/v1",
                0.93,
                List.of("explicit_note_mention"),
                RouteStrategy.ANSWER_ONLY,
                null
            ),
            null
        ));
        when(provider.plannerPromptVersion()).thenReturn("test-planner");

        AiWriteProviderSelector providerSelector = mock(AiWriteProviderSelector.class);
        when(providerSelector.activeProvider()).thenReturn(provider);

        NoteWorkflowService noteWorkflowService = mock(NoteWorkflowService.class);
        when(noteWorkflowService.loadDocument(21L)).thenReturn(new NoteDocumentV1(
            new NoteDocumentMeta(21L, "Deploy Runbook", "Deployment notes", "generic_note/v1", "v1", 2L, 7L),
            List.of(
                new NoteSection("summary", "Summary", "summary", "Deployment and rollback steps for NotesZero."),
                new NoteSection("body", "Body", "body", "Deploy order: backend build, migrations, frontend release. Rollback uses the previous green build.")
            )
        ));

        ChatCommitService service = new ChatCommitService(
            chatEventRepository,
            mock(NotebookRepository.class),
            mock(NoteRepository.class),
            retrievalService,
            null,
            providerSelector,
            noteWorkflowService,
            new CanonicalNoteTemplates(),
            new ObjectMapper(),
            aiProperties
        );

        CommitChatRequest request = new CommitChatRequest();
        request.setMessage("What does Deploy Runbook say about rollback?");

        CommitChatResponse response = service.commit(request);

        assertThat(response.answer()).containsIgnoringCase("rollback");
        assertThat(response.answer()).containsIgnoringCase("green build");
        verify(noteWorkflowService).loadDocument(21L);
    }
}
