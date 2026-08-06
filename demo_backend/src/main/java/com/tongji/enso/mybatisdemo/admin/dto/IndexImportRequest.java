package com.tongji.enso.mybatisdemo.admin.dto;

public class IndexImportRequest {

    private String dataset;
    private String year;
    private String month;
    private String varModel;
    private Boolean overwrite;

    private String source;
    private Integer leadMonths;
    private String smoothing;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Integer getLeadMonths() {
        return leadMonths;
    }

    public void setLeadMonths(Integer leadMonths) {
        this.leadMonths = leadMonths;
    }

    public String getSmoothing() {
        return smoothing;
    }

    public void setSmoothing(String smoothing) {
        this.smoothing = smoothing;
    }

    public String getDataset() {
        return dataset;
    }

    public void setDataset(String dataset) {
        this.dataset = dataset;
    }

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

    public String getVarModel() {
        return varModel;
    }

    public void setVarModel(String varModel) {
        this.varModel = varModel;
    }

    public Boolean getOverwrite() {
        return overwrite;
    }

    public void setOverwrite(Boolean overwrite) {
        this.overwrite = overwrite;
    }
}
