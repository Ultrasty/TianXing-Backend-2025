package com.tongji.enso.mybatisdemo.admin.evaluation;

public class EvaluationCreateRequest extends EvaluationRecordRequest {
    private String category;

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
