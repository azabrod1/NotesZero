package com.notesapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.domain.Notebook;
import com.notesapp.service.routing.DeterministicRoutingService;
import com.notesapp.service.routing.RoutingDecision;
import com.notesapp.service.routing.RoutingInput;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicRoutingServiceTest {

    private final DeterministicRoutingService routingService = new DeterministicRoutingService(new NoteContentHelper(new ObjectMapper()));

    @Test
    void routesDogTextToDogNotebook() {
        Notebook dog = new Notebook(1L, "Dog notes", "Dog info", Instant.now());
        setId(dog, 1L);
        Notebook work = new Notebook(1L, "Work websites", "Work links", Instant.now());
        setId(work, 2L);
        Notebook health = new Notebook(1L, "Family health", "Health notes", Instant.now());
        setId(health, 3L);

        RoutingDecision decision = routingService.route(new RoutingInput(
            "my dog just pooped in the backyard",
            List.of(dog, work, health),
            List.of()
        ));

        assertTrue(decision.getNotebookId().isPresent());
        assertEquals(1L, decision.getNotebookId().get());
        assertTrue(decision.getConfidence() >= 0.72);
    }

    private void setId(Notebook notebook, Long id) {
        try {
            java.lang.reflect.Field field = Notebook.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(notebook, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
