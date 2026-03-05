package com.notesapp.service.ai;

import com.notesapp.config.AiProperties;
import com.notesapp.service.extraction.ExtractedFact;
import com.notesapp.service.routing.RoutingDecision;
import com.notesapp.service.routing.RoutingInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class AnthropicAiProviderClient implements AiProviderClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicAiProviderClient.class);
    private static final BigDecimal ESTIMATED_REQUEST_COST_GBP = new BigDecimal("0.0040");

    private final AiProperties aiProperties;
    private final AiBudgetGuard aiBudgetGuard;
    private final DeterministicAiProviderClient fallback;

    public AnthropicAiProviderClient(AiProperties aiProperties, AiBudgetGuard aiBudgetGuard,
                                     DeterministicAiProviderClient fallback) {
        this.aiProperties = aiProperties;
        this.aiBudgetGuard = aiBudgetGuard;
        this.fallback = fallback;
    }

    @Override
    public List<ExtractedFact> extractFacts(String rawText) {
        return runWithBudget(() -> fallback.extractFacts(rawText));
    }

    @Override
    public RoutingDecision routeNotebook(RoutingInput input) {
        return runWithBudget(() -> fallback.routeNotebook(input));
    }

    @Override
    public String providerName() {
        return "anthropic";
    }

    private <T> T runWithBudget(SupplierWithException<T> supplier) {
        if (aiProperties.getAnthropicApiKey() == null || aiProperties.getAnthropicApiKey().isBlank()) {
            log.warn("Anthropic provider selected but API key not configured. Falling back to deterministic provider.");
            return fallbackValue(supplier);
        }
        aiBudgetGuard.registerCost(ESTIMATED_REQUEST_COST_GBP);
        return fallbackValue(supplier);
    }

    private <T> T fallbackValue(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new IllegalStateException("Anthropic provider call failed", e);
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }
}
