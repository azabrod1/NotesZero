package com.notesapp.domain;

import com.notesapp.domain.enums.FactValueType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "facts")
public class Fact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id", nullable = false)
    private Note note;

    @Column(name = "notebook_id", nullable = false)
    private Long notebookId;

    @Column(name = "key_name", nullable = false, length = 128)
    private String keyName;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 24)
    private FactValueType valueType;

    @Column(name = "value_number", precision = 14, scale = 4)
    private BigDecimal valueNumber;

    @Column(name = "value_text", length = 4000)
    private String valueText;

    @Column(name = "value_datetime")
    private Instant valueDatetime;

    @Column(length = 24)
    private String unit;

    @Column(nullable = false)
    private double confidence;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Fact() {
    }

    public Fact(Note note, Long notebookId, String keyName, FactValueType valueType, BigDecimal valueNumber, String valueText,
                Instant valueDatetime, String unit, double confidence, Instant createdAt) {
        this.note = note;
        this.notebookId = notebookId;
        this.keyName = keyName;
        this.valueType = valueType;
        this.valueNumber = valueNumber;
        this.valueText = valueText;
        this.valueDatetime = valueDatetime;
        this.unit = unit;
        this.confidence = confidence;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public Long getNotebookId() {
        return notebookId;
    }

    public void setNotebookId(Long notebookId) {
        this.notebookId = notebookId;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public FactValueType getValueType() {
        return valueType;
    }

    public void setValueType(FactValueType valueType) {
        this.valueType = valueType;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public void setValueNumber(BigDecimal valueNumber) {
        this.valueNumber = valueNumber;
    }

    public String getValueText() {
        return valueText;
    }

    public void setValueText(String valueText) {
        this.valueText = valueText;
    }

    public Instant getValueDatetime() {
        return valueDatetime;
    }

    public void setValueDatetime(Instant valueDatetime) {
        this.valueDatetime = valueDatetime;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

