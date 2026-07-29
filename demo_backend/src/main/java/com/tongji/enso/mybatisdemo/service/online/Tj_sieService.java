package com.tongji.enso.mybatisdemo.service.online;

import com.tongji.enso.mybatisdemo.entity.online.Tj_sie;
import com.tongji.enso.mybatisdemo.mapper.online.Tj_sieMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class Tj_sieService {
    @Autowired
    private Tj_sieMapper tj_sieMapper;

    public List<Tj_sie> findAllSIE(){
        return tj_sieMapper.findAll();
    }

    public List<Tj_sie> findSIEByMonth(String year, String month){
        return tj_sieMapper.findByMonth(year,month);
    }

    public List<Tj_sie> findByModelandMonth(String year, String month,String var_model) {
        return tj_sieMapper.findByModelandMonth(year, month, var_model);
    }

    public List<Tj_sie> findByYear(String year) {
        return tj_sieMapper.findByYear(year);
    }

    public List<String> findErrorAnalysisAvailableYears() {
        return tj_sieMapper.findErrorAnalysisAvailableYears();
    }

    //修改开始
    public List<String> findAvailableYears() {
        return tj_sieMapper.selectDistinctYears();
    }

    public List<String> findAvailableMonths() {
        return tj_sieMapper.selectDistinctMonths();
    }

    public Map<String, String> findLatestDate() {
        return tj_sieMapper.selectLatestYearMonth();
    }
    //修改结束
    

}
