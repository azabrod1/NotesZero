package com.notesapp.service.ai;

import com.notesapp.config.AiProperties;
import org.springframework.stereotype.Component;

@Component
public class AiProviderSelector {

    private final AiProperties aiProperties;
    private final DeterministicAiProviderClient deterministic;
    private final AnthropicAiProviderClient anthropic;
    private final OpenAiProviderClient openAi;

    public AiProviderSelector(AiProperties aiProperties, DeterministicAiProviderClient deterministic,
                              AnthropicAiProviderClient anthropic, OpenAiProviderClient openAi) {
        this.aiProperties = aiProperties;
        this.deterministic = deterministic;
        this.anthropic = anthropic;
        this.openAi = openAi;
    }

    public AiProviderClient activeClient() {
        String provider = aiProperties.getProvider();
        if ("anthropic".equalsIgnoreCase(provider)) {
            return anthropic;
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return openAi;
        }
        return deterministic;
    }
}
