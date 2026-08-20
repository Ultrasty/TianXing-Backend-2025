package com.tongji.enso.mybatisdemo.mapper.online;

import com.tongji.enso.mybatisdemo.entity.online.Imgs;
import com.tongji.enso.mybatisdemo.entity.online.Tj_enso;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;


@Mapper
public interface EnsoMapper {

    /**
     * 从 tj_enso 表中查询指定年、月、变量模型的数据
     *
     * @param year
     * @param month
     * @param var_model
     * @return
     */
    @Select("SELECT data FROM tj_enso WHERE year = #{year} AND month = #{month} AND var_model = #{var_model}")
    String findEachPredictionsResultByMonthType(@Param("year") String year, @Param("month") String month, @Param("var_model") String var_model);

    /**
     * 从 tj_enso 中查询指定类型的数据
     * @param var_model
     * @return
     */
    @Select("SELECT * FROM tj_enso WHERE var_model = #{var_model}")
    List<Tj_enso> findTj_ensoInfoByType(@Param("var_model") String var_model);

    /**
     * 从 obs_enso 表中查询指定年的数据
     */
    @Select("SELECT data FROM obs_enso WHERE year = #{year}")
    String findObsEnsoByYear(@Param("year") String year);

}
