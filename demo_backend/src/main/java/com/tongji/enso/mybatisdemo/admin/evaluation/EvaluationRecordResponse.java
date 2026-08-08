package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationRecordResponse {
    private final EvaluationCategory category;
    private final Long id;
    private final String year;
    private final String month;
    private final String day;
    private final String varModel;
    private final JsonNode data;

    public EvaluationRecordResponse(EvaluationCategory category, Long id, String year, String month,
                                    String day, String varModel, JsonNode data) {
        this.category = category;
        this.id = id;
        this.year = year;
        this.month = month;
        this.day = day;
        this.varModel = varModel;
        this.data = data;
    }

    public EvaluationCategory getCategory() {
        return category;
    }

    public Long getId() {
        return id;
    }

    public String getYear() {
        return year;
    }

    public String getMonth() {
        return month;
    }

    public String getDay() {
        return day;
    }

    public String getVarModel() {
        return varModel;
    }

    public JsonNode getData() {
        return data;
    }
}
