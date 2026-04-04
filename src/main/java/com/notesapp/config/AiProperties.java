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
    private double routeAutoApplyThreshold = 0.62;
    private String triageModel = "gpt-5.4-nano";
    private String routerModel = "gpt-5.4-mini";
    private String plannerModel = "gpt-5.4-mini";
    private String summaryModel = "gpt-5.4-mini";
    private String routerPromptId = "";
    private String plannerPromptId = "";
    private String summaryPromptId = "";
    private String openAiBaseUrl = "https://api.openai.com/v1";
    private int chatCommitRateLimitCount = 100;
    private int chatCommitRateLimitWindowMinutes = 30;
    private String retrievalMode = "hybrid";

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

    public double getRouteAutoApplyThreshold() {
        return routeAutoApplyThreshold;
    }

    public void setRouteAutoApplyThreshold(double routeAutoApplyThreshold) {
        this.routeAutoApplyThreshold = routeAutoApplyThreshold;
    }

    public String getTriageModel() {
        return triageModel;
    }

    public void setTriageModel(String triageModel) {
        this.triageModel = triageModel;
    }

    public String getRouterModel() {
        return routerModel;
    }

    public void setRouterModel(String routerModel) {
        this.routerModel = routerModel;
    }

    public String getPlannerModel() {
        return plannerModel;
    }

    public void setPlannerModel(String plannerModel) {
        this.plannerModel = plannerModel;
    }

    public String getSummaryModel() {
        return summaryModel;
    }

    public void setSummaryModel(String summaryModel) {
        this.summaryModel = summaryModel;
    }

    public String getRouterPromptId() {
        return routerPromptId;
    }

    public void setRouterPromptId(String routerPromptId) {
        this.routerPromptId = routerPromptId;
    }

    public String getPlannerPromptId() {
        return plannerPromptId;
    }

    public void setPlannerPromptId(String plannerPromptId) {
        this.plannerPromptId = plannerPromptId;
    }

    public String getSummaryPromptId() {
        return summaryPromptId;
    }

    public void setSummaryPromptId(String summaryPromptId) {
        this.summaryPromptId = summaryPromptId;
    }

    public String getOpenAiBaseUrl() {
        return openAiBaseUrl;
    }

    public void setOpenAiBaseUrl(String openAiBaseUrl) {
        this.openAiBaseUrl = openAiBaseUrl;
    }

    public int getChatCommitRateLimitCount() {
        return chatCommitRateLimitCount;
    }

    public void setChatCommitRateLimitCount(int chatCommitRateLimitCount) {
        this.chatCommitRateLimitCount = chatCommitRateLimitCount;
    }

    public int getChatCommitRateLimitWindowMinutes() {
        return chatCommitRateLimitWindowMinutes;
    }

    public void setChatCommitRateLimitWindowMinutes(int chatCommitRateLimitWindowMinutes) {
        this.chatCommitRateLimitWindowMinutes = chatCommitRateLimitWindowMinutes;
    }

    public String getRetrievalMode() {
        return retrievalMode;
    }

    public void setRetrievalMode(String retrievalMode) {
        this.retrievalMode = retrievalMode;
    }
}
