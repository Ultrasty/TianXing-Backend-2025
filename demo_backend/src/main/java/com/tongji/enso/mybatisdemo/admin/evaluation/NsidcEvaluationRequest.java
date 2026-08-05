package com.tongji.enso.mybatisdemo.admin.evaluation;

public class NsidcEvaluationRequest {
    private String category;
    private String year;
    private String month;
    private String day;
    private Integer leadStartOffsetDays;
    private String mode;

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }
    public String getDay() { return day; }
    public void setDay(String day) { this.day = day; }
    public Integer getLeadStartOffsetDays() { return leadStartOffsetDays; }
    public void setLeadStartOffsetDays(Integer leadStartOffsetDays) { this.leadStartOffsetDays = leadStartOffsetDays; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
