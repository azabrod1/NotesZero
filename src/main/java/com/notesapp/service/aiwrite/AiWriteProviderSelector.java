package com.notesapp.service.aiwrite;

import com.notesapp.config.AiProperties;
import org.springframework.stereotype.Component;

@Component
public class AiWriteProviderSelector {

    private final AiProperties aiProperties;
    private final MockAiWriteProvider mockAiWriteProvider;
    private final OpenAiResponsesAiWriteProvider openAiResponsesAiWriteProvider;

    public AiWriteProviderSelector(AiProperties aiProperties,
                                   MockAiWriteProvider mockAiWriteProvider,
                                   OpenAiResponsesAiWriteProvider openAiResponsesAiWriteProvider) {
        this.aiProperties = aiProperties;
        this.mockAiWriteProvider = mockAiWriteProvider;
        this.openAiResponsesAiWriteProvider = openAiResponsesAiWriteProvider;
    }

    public AiWriteProvider activeProvider() {
        if ("openai".equalsIgnoreCase(aiProperties.getProvider())) {
            return openAiResponsesAiWriteProvider.isConfigured()
                ? openAiResponsesAiWriteProvider
                : mockAiWriteProvider;
        }
        return mockAiWriteProvider;
    }
}
