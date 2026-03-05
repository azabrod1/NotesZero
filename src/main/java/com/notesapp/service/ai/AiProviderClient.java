package com.notesapp.service.ai;

import com.notesapp.service.extraction.ExtractedFact;
import com.notesapp.service.routing.RoutingDecision;
import com.notesapp.service.routing.RoutingInput;

import java.util.List;

public interface AiProviderClient {

    List<ExtractedFact> extractFacts(String rawText);

    RoutingDecision routeNotebook(RoutingInput input);

    String providerName();
}
