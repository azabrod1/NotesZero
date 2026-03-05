package com.notesapp.service.ai;

import com.notesapp.service.extraction.ExtractedFact;
import com.notesapp.service.extraction.FactExtractionService;
import com.notesapp.service.routing.DeterministicRoutingService;
import com.notesapp.service.routing.RoutingDecision;
import com.notesapp.service.routing.RoutingInput;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeterministicAiProviderClient implements AiProviderClient {

    private final FactExtractionService factExtractionService;
    private final DeterministicRoutingService deterministicRoutingService;

    public DeterministicAiProviderClient(FactExtractionService factExtractionService,
                                         DeterministicRoutingService deterministicRoutingService) {
        this.factExtractionService = factExtractionService;
        this.deterministicRoutingService = deterministicRoutingService;
    }

    @Override
    public List<ExtractedFact> extractFacts(String rawText) {
        return factExtractionService.extract(rawText);
    }

    @Override
    public RoutingDecision routeNotebook(RoutingInput input) {
        return deterministicRoutingService.route(input);
    }

    @Override
    public String providerName() {
        return "mock";
    }
}
