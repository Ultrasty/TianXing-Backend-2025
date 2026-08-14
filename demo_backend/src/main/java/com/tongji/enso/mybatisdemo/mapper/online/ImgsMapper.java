package com.tongji.enso.mybatisdemo.mapper.online;

import com.tongji.enso.mybatisdemo.entity.online.Imgs;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.apache.ibatis.annotations.Select;
import org.springframework.web.bind.annotation.RequestParam;

@Repository
public interface ImgsMapper {

    String findSICByDate(String year, String month, String day);

    String findNAOByMonth(String year, String month);

    String findNAOCORRByMonth(String year, String month);

    List<Imgs> findAllByType(String type);

    /**
     * 从 imgs 中查询指定年、月、日、类型的数据
     * @return
     */
    @Select("SELECT * FROM imgs WHERE year = #{year} AND month = #{month} AND day = #{day} AND type = #{type}")
    List<Imgs> findImgsInfoByDayType(@Param("year") String year, @Param("month") String month, @Param("day") String day, @Param("type") String type);

    /**
     * 从 imgs 中查询指定类型的数据
     * @param type
     * @return
     */
    @Select("SELECT * FROM imgs WHERE type = #{type}")
    List<Imgs> findImgsInfoByType(@Param("type") String type);

    @Select("SELECT COUNT(*) FROM imgs WHERE year = #{year} AND month = #{month} AND day = #{day} AND type = #{type}")
    int countByYearMonthDayType(@Param("year") String year, @Param("month") String month, @Param("day") String day, @Param("type") String type);

    @Select("SELECT COUNT(*) FROM imgs WHERE year = #{year} AND month = #{month} AND type = #{type}")
    int countByYearMonthType(@Param("year") String year, @Param("month") String month, @Param("type") String type);

    @Insert("INSERT INTO imgs (year, month, day, type, data) VALUES (#{year}, #{month}, #{day}, #{type}, #{data})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertImgs(Imgs imgs);


}
