package com.tongji.enso.mybatisdemo.admin.evaluation;

public class EvaluationPersistenceRecord {
    private Long id;
    private String year;
    private String month;
    private String day;
    private String varModel;
    private String data;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
