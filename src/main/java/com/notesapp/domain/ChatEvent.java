package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "chat_events")
public class ChatEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 12000)
    private String message;

    @ManyToOne
    @JoinColumn(name = "selected_notebook_id")
    private Notebook selectedNotebook;

    @ManyToOne
    @JoinColumn(name = "selected_note_id")
    private Note selectedNote;

    @Column(name = "route_plan_json", length = 500000)
    private String routePlanJson;

    @Column(name = "patch_plan_json", length = 500000)
    private String patchPlanJson;

    @Column(name = "apply_result_json", length = 500000)
    private String applyResultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Notebook getSelectedNotebook() {
        return selectedNotebook;
    }

    public void setSelectedNotebook(Notebook selectedNotebook) {
        this.selectedNotebook = selectedNotebook;
    }

    public Note getSelectedNote() {
        return selectedNote;
    }

    public void setSelectedNote(Note selectedNote) {
        this.selectedNote = selectedNote;
    }

    public String getRoutePlanJson() {
        return routePlanJson;
    }

    public void setRoutePlanJson(String routePlanJson) {
        this.routePlanJson = routePlanJson;
    }

    public String getPatchPlanJson() {
        return patchPlanJson;
    }

    public void setPatchPlanJson(String patchPlanJson) {
        this.patchPlanJson = patchPlanJson;
    }

    public String getApplyResultJson() {
        return applyResultJson;
    }

    public void setApplyResultJson(String applyResultJson) {
        this.applyResultJson = applyResultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
