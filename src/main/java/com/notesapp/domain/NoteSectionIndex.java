package com.notesapp.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "note_section_index")
public class NoteSectionIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(name = "section_id", nullable = false, length = 100)
    private String sectionId;

    @Column(name = "section_kind", nullable = false, length = 50)
    private String sectionKind;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Column(name = "entity_tags", length = 4000)
    private String entityTags;

    @Column(name = "section_digest", length = 500)
    private String sectionDigest;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    public NoteSectionIndex() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }

    public String getSectionId() { return sectionId; }
    public void setSectionId(String sectionId) { this.sectionId = sectionId; }

    public String getSectionKind() { return sectionKind; }
    public void setSectionKind(String sectionKind) { this.sectionKind = sectionKind; }

    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }

    public String getEntityTags() { return entityTags; }
    public void setEntityTags(String entityTags) { this.entityTags = entityTags; }

    public String getSectionDigest() { return sectionDigest; }
    public void setSectionDigest(String sectionDigest) { this.sectionDigest = sectionDigest; }

    public Instant getRefreshedAt() { return refreshedAt; }
    public void setRefreshedAt(Instant refreshedAt) { this.refreshedAt = refreshedAt; }
}
