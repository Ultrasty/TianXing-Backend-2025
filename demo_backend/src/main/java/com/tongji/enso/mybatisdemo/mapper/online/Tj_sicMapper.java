package com.tongji.enso.mybatisdemo.mapper.online;


import com.tongji.enso.mybatisdemo.entity.online.Tj_sic;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public interface Tj_sicMapper {

    List<Tj_sic> findAll();

    Tj_sic findByDate(String year, String month, String day);

    List<Tj_sic> findErrorByMonth(String year, String month);

    List<Tj_sic> findErrorBoxByYearAndModel(String year);
    List<Map<String, String>> findErrorAvailableMonths();
    List<String> findErrorBoxAvailableYears();
    void insertTj_sic(Tj_sic tjSic);
    List<String> findDistinctYears();
    List<String> findDistinctMonthsByYear(String year);
    List<String> findDistinctDaysByYearAndMonth(String year, String month);
    Map<String, String> findLatestDate();
}
