package com.tongji.enso.mybatisdemo.admin.dto;

/** 新增/更新预报数据请求。data 必须是合法 JSON 数组。 */
public class ForecastDataRequest {
    private String dataset;
    private String year;
    private String month;
    private String varModel;
    private String data;

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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
