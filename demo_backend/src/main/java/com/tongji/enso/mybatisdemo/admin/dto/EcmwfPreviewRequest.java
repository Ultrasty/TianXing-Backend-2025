package com.tongji.enso.mybatisdemo.admin.dto;

public class EcmwfPreviewRequest {
    private String date;
    private Integer time;
    private Integer step;
    private String param;
    private String levtype;
    private Integer levelist;
    private String stream;
    private String forecastType;
    private String provider;
    private String model;
    private String reducer;
    private Integer maxPoints;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Integer getTime() {
        return time;
    }

    public void setTime(Integer time) {
        this.time = time;
    }

    public Integer getStep() {
        return step;
    }

    public void setStep(Integer step) {
        this.step = step;
    }

    public String getParam() {
        return param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public String getLevtype() {
        return levtype;
    }

    public void setLevtype(String levtype) {
        this.levtype = levtype;
    }

    public Integer getLevelist() {
        return levelist;
    }

    public void setLevelist(Integer levelist) {
        this.levelist = levelist;
    }

    public String getStream() {
        return stream;
    }

    public void setStream(String stream) {
        this.stream = stream;
    }

    public String getForecastType() {
        return forecastType;
    }

    public void setForecastType(String forecastType) {
        this.forecastType = forecastType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getReducer() {
        return reducer;
    }

    public void setReducer(String reducer) {
        this.reducer = reducer;
    }

    public Integer getMaxPoints() {
        return maxPoints;
    }

    public void setMaxPoints(Integer maxPoints) {
        this.maxPoints = maxPoints;
    }
}
