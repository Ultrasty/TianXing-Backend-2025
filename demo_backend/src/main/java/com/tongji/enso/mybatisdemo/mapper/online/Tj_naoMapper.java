package com.tongji.enso.mybatisdemo.mapper.online;

import com.tongji.enso.mybatisdemo.entity.online.Tj_nao;

import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Tj_naoMapper {

    Tj_nao findByMonthAndModel(String year, String month);

    Tj_nao findGridByMonth(String year, String month);

    List<Tj_nao> findAllByModel(String var_model);
    List<String> findAvailableYears();
    List<String> findMonthsByYear(String year);
    @Select("SELECT * FROM nao_prediction")
    List<Tj_nao> findAll();
}
