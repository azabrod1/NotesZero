package com.notesapp.web.dto;

import com.notesapp.domain.enums.FactValueType;

import java.math.BigDecimal;
import java.time.Instant;

public class FactResponse {

    private Long id;
    private String keyName;
    private FactValueType valueType;
    private BigDecimal valueNumber;
    private String valueText;
    private Instant valueDatetime;
    private String unit;
    private double confidence;

    public FactResponse() {
    }

    public FactResponse(Long id, String keyName, FactValueType valueType, BigDecimal valueNumber, String valueText,
                        Instant valueDatetime, String unit, double confidence) {
        this.id = id;
        this.keyName = keyName;
        this.valueType = valueType;
        this.valueNumber = valueNumber;
        this.valueText = valueText;
        this.valueDatetime = valueDatetime;
        this.unit = unit;
        this.confidence = confidence;
    }

    public Long getId() {
        return id;
    }

    public String getKeyName() {
        return keyName;
    }

    public FactValueType getValueType() {
        return valueType;
    }

    public BigDecimal getValueNumber() {
        return valueNumber;
    }

    public String getValueText() {
        return valueText;
    }

    public Instant getValueDatetime() {
        return valueDatetime;
    }

    public String getUnit() {
        return unit;
    }

    public double getConfidence() {
        return confidence;
    }
}
