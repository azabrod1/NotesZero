package com.notesapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notesapp.service.extraction.ExtractedFact;
import com.notesapp.service.extraction.FactExtractionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactExtractionServiceTest {

    private final FactExtractionService service = new FactExtractionService(new NoteContentHelper(new ObjectMapper()));

    @Test
    void extractsNumericFactsFromInventoryAndConsumptionText() {
        List<ExtractedFact> facts = service.extract("I have 8 pies in the fridge and ate two apples");

        Optional<ExtractedFact> pies = facts.stream().filter(f -> "pie_count".equals(f.getKeyName())).findFirst();
        Optional<ExtractedFact> apples = facts.stream().filter(f -> "apple_eaten".equals(f.getKeyName())).findFirst();

        assertTrue(pies.isPresent(), "pie_count should be extracted");
        assertEquals(8, pies.get().getNumberValue().intValue());
        assertTrue(apples.isPresent(), "apple_eaten should be extracted");
        assertEquals(2, apples.get().getNumberValue().intValue());
    }

    @Test
    void marksFeverWithoutUnitAsHighRisk() {
        List<ExtractedFact> facts = service.extract("baby had fever of 103");

        ExtractedFact temperature = facts.stream()
            .filter(fact -> "body_temperature".equals(fact.getKeyName()))
            .findFirst()
            .orElseThrow();

        assertTrue(temperature.isHighRisk());
        assertEquals(103, temperature.getNumberValue().intValue());
    }
}
