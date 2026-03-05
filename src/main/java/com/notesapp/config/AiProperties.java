package com.notesapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "notes.ai")
public class AiProperties {

    private String provider = "mock";
    private double notebookRoutingHighRiskThreshold = 0.72;
    private double monthlyBudgetGbp = 50.0;
    private String anthropicApiKey = "";
    private String openAiApiKey = "";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public double getNotebookRoutingHighRiskThreshold() {
        return notebookRoutingHighRiskThreshold;
    }

    public void setNotebookRoutingHighRiskThreshold(double notebookRoutingHighRiskThreshold) {
        this.notebookRoutingHighRiskThreshold = notebookRoutingHighRiskThreshold;
    }

    public double getMonthlyBudgetGbp() {
        return monthlyBudgetGbp;
    }

    public void setMonthlyBudgetGbp(double monthlyBudgetGbp) {
        this.monthlyBudgetGbp = monthlyBudgetGbp;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
    }

    public String getOpenAiApiKey() {
        return openAiApiKey;
    }

    public void setOpenAiApiKey(String openAiApiKey) {
        this.openAiApiKey = openAiApiKey;
    }
}
