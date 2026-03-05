package com.notesapp.web.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class ChartPointResponse {

    private Instant at;
    private BigDecimal value;

    public ChartPointResponse() {
    }

    public ChartPointResponse(Instant at, BigDecimal value) {
        this.at = at;
        this.value = value;
    }

    public Instant getAt() {
        return at;
    }

    public BigDecimal getValue() {
        return value;
    }
}
