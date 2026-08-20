package com.tongji.enso.mybatisdemo.admin.evaluation;

public class EvaluationMetricProvenance {
    private Long id;
    private String category;
    private Long recordId;
    private String source;
    private String predictionModel;
    private String observationDataset;
    private String observationVersion;
    private String details;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getPredictionModel() { return predictionModel; }
    public void setPredictionModel(String predictionModel) { this.predictionModel = predictionModel; }
    public String getObservationDataset() { return observationDataset; }
    public void setObservationDataset(String observationDataset) { this.observationDataset = observationDataset; }
    public String getObservationVersion() { return observationVersion; }
    public void setObservationVersion(String observationVersion) { this.observationVersion = observationVersion; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
