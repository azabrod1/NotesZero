package com.notesapp.web.dto;

import java.util.ArrayList;
import java.util.List;

public class ChartSeriesResponse {

    private String keyName;
    private List<ChartPointResponse> points = new ArrayList<>();

    public ChartSeriesResponse() {
    }

    public ChartSeriesResponse(String keyName, List<ChartPointResponse> points) {
        this.keyName = keyName;
        this.points = points;
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public List<ChartPointResponse> getPoints() {
        return points;
    }

    public void setPoints(List<ChartPointResponse> points) {
        this.points = points;
    }
}
