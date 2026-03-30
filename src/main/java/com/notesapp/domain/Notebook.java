package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "notebooks")
public class Notebook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "routing_summary", length = 500)
    private String routingSummary;

    @Column(name = "include_examples", length = 2000)
    private String includeExamples;

    @Column(name = "exclude_examples", length = 2000)
    private String excludeExamples;

    @Column(name = "last_summary_refresh_at")
    private Instant lastSummaryRefreshAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Notebook() {
    }

    public Notebook(Long userId, String name, String description, Instant createdAt) {
        this.userId = userId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRoutingSummary() {
        return routingSummary;
    }

    public void setRoutingSummary(String routingSummary) {
        this.routingSummary = routingSummary;
    }

    public String getIncludeExamples() {
        return includeExamples;
    }

    public void setIncludeExamples(String includeExamples) {
        this.includeExamples = includeExamples;
    }

    public String getExcludeExamples() {
        return excludeExamples;
    }

    public void setExcludeExamples(String excludeExamples) {
        this.excludeExamples = excludeExamples;
    }

    public Instant getLastSummaryRefreshAt() {
        return lastSummaryRefreshAt;
    }

    public void setLastSummaryRefreshAt(Instant lastSummaryRefreshAt) {
        this.lastSummaryRefreshAt = lastSummaryRefreshAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

