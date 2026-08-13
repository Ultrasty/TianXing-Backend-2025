package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EvaluationBatchImportRequest {
    private String source;
    private String mode;
    private String category;
    private List<EvaluationRecordRequest> records;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<EvaluationRecordRequest> getRecords() {
        return records;
    }

    public void setRecords(List<EvaluationRecordRequest> records) {
        this.records = records;
    }
}
