package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;

public class EvaluationRecordRequest {
    private String year;
    private String month;
    private String day;
    private String varModel;
    private JsonNode data;
    private String source;

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public String getVarModel() {
        return varModel;
    }

    public void setVarModel(String varModel) {
        this.varModel = varModel;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
