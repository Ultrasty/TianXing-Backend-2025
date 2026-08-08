package com.tongji.enso.mybatisdemo.admin.dto;

/**
 * 从 ECMWF Open Data 拉取一个字段并发布为新的预报数据。
 * date 可为空；为空时由 ecmwf-opendata 选择最新可用起报。
 */
public class EcmwfImportRequest {
    private String dataset;
    private String year;
    private String month;
    private String varModel;

    private String date;
    private Integer time;
    private Integer step;
    private String param;
    private String levtype;
    private Integer levelist;
    private String stream;
    private String type;
    private String source;
    private String model;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
