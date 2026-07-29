package com.tongji.enso.mybatisdemo.mapper.online;

import com.tongji.enso.mybatisdemo.entity.online.Meteo;
import com.tongji.enso.mybatisdemo.entity.online.Tj_sie;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface Tj_sieMapper {

    /**
     * 查询全部SIE指数
     */
    List<Tj_sie> findAll();

    /**
     * 查询某年某月之后12个月的SIE指数
     */
    List<Tj_sie> findByMonth(String year, String month);

    /**
     * 查询指定var_model以及指定月份月份开始起报之后12个月的SIE数据
     */
    List<Tj_sie> findByModelandMonth(String year, String month,String var_model);


    List<Tj_sie> findByYear(String year);
    List<String> findErrorAnalysisAvailableYears();
    //新增开始
    List<String> selectDistinctYears();
    List<String> selectDistinctMonths();
    Map<String, String> selectLatestYearMonth();
    //新增结束
}
