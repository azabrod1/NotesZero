package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "pages")
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notebook_id", nullable = false)
    private Notebook notebook;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "content_current", nullable = false, length = 20000)
    private String contentCurrent;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Page() {
    }

    public Page(Notebook notebook, String title, String contentCurrent, Instant updatedAt) {
        this.notebook = notebook;
        this.title = title;
        this.contentCurrent = contentCurrent;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Notebook getNotebook() {
        return notebook;
    }

    public void setNotebook(Notebook notebook) {
        this.notebook = notebook;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContentCurrent() {
        return contentCurrent;
    }

    public void setContentCurrent(String contentCurrent) {
        this.contentCurrent = contentCurrent;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

