package com.notesapp.service.extraction;

import com.notesapp.domain.enums.FactValueType;

import java.math.BigDecimal;
import java.time.Instant;

public class ExtractedFact {

    private final String keyName;
    private final FactValueType valueType;
    private final BigDecimal numberValue;
    private final String textValue;
    private final Instant datetimeValue;
    private final String unit;
    private final double confidence;
    private final boolean highRisk;
    private final String clarificationQuestion;

    public ExtractedFact(String keyName, FactValueType valueType, BigDecimal numberValue, String textValue,
                         Instant datetimeValue, String unit, double confidence, boolean highRisk,
                         String clarificationQuestion) {
        this.keyName = keyName;
        this.valueType = valueType;
        this.numberValue = numberValue;
        this.textValue = textValue;
        this.datetimeValue = datetimeValue;
        this.unit = unit;
        this.confidence = confidence;
        this.highRisk = highRisk;
        this.clarificationQuestion = clarificationQuestion;
    }

    public static ExtractedFact number(String keyName, BigDecimal value, String unit, double confidence) {
        return new ExtractedFact(keyName, FactValueType.NUMBER, value, null, null, unit, confidence, false, null);
    }

    public static ExtractedFact text(String keyName, String value, double confidence) {
        return new ExtractedFact(keyName, FactValueType.TEXT, null, value, null, null, confidence, false, null);
    }

    public static ExtractedFact highRiskNumber(String keyName, BigDecimal value, String unit, double confidence,
                                               String clarificationQuestion) {
        return new ExtractedFact(keyName, FactValueType.NUMBER, value, null, null, unit, confidence, true, clarificationQuestion);
    }

    public String getKeyName() {
        return keyName;
    }

    public FactValueType getValueType() {
        return valueType;
    }

    public BigDecimal getNumberValue() {
        return numberValue;
    }

    public String getTextValue() {
        return textValue;
    }

    public Instant getDatetimeValue() {
        return datetimeValue;
    }

    public String getUnit() {
        return unit;
    }

    public double getConfidence() {
        return confidence;
    }

    public boolean isHighRisk() {
        return highRisk;
    }

    public String getClarificationQuestion() {
        return clarificationQuestion;
    }
}
