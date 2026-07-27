package com.tongji.enso.mybatisdemo.admin.evaluation;

public class EvaluationQueryRequest {
    private String year;
    private String month;
    private String day;
    private String varModel;
    private int page = 1;
    private int pageSize = 20;

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

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
