package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface AdminEvaluationMapper {
    String NAO_PREDICATE = "var_model IN ('corr_lead1-6_ECMWF','corr_lead1-6_ECCC','corr_lead1-6_NAO-MCD')";
    String SIC_PREDICATE = "(var_model IN ('MITgcm(with DA)withBC_RMSE','withDA_withoutBC_RMSE'," +
            "'withoutDA_withBC_RMSE','withoutDA_withoutBC') OR " +
            "var_model REGEXP '^[0-9]{4}(_per)?_(BACC|RMSE)$')";
    String SIE_PREDICATE = "var_model IN ('RMSD','BAIS','VAR','CORRELATION','OBS_STD','PRE_STD')";

    @Select({"<script>",
            "SELECT id, year, data FROM obs_enso WHERE 1=1",
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "ORDER BY CAST(year AS DECIMAL) DESC, id DESC LIMIT #{offset}, #{limit}",
            "</script>"})
    List<EvaluationPersistenceRecord> listEnso(@Param("q") EvaluationQueryRequest query,
                                                @Param("offset") int offset, @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(*) FROM obs_enso WHERE 1=1",
            "<if test='q.year != null'> AND year=#{q.year}</if>", "</script>"})
    long countEnso(@Param("q") EvaluationQueryRequest query);

    @Select("SELECT id, year, data FROM obs_enso WHERE id=#{id}")
    EvaluationPersistenceRecord getEnso(@Param("id") long id);

    @Select("SELECT id, year, data FROM obs_enso WHERE year=#{year} LIMIT 1")
    EvaluationPersistenceRecord findEnsoKey(@Param("year") String year);

    @Insert("INSERT INTO obs_enso(year,data) VALUES(#{year},#{data})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertEnso(EvaluationPersistenceRecord record);

    @Update("UPDATE obs_enso SET year=#{year}, data=#{data} WHERE id=#{id}")
    int updateEnso(EvaluationPersistenceRecord record);

    @Delete("DELETE FROM obs_enso WHERE id=#{id}")
    int deleteEnso(@Param("id") long id);

    @Select({"<script>",
            "SELECT id, year, month, var_model AS varModel, data FROM tj_nao WHERE " + NAO_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>",
            "ORDER BY id DESC LIMIT #{offset}, #{limit}",
            "</script>"})
    List<EvaluationPersistenceRecord> listNao(@Param("q") EvaluationQueryRequest query,
                                               @Param("offset") int offset, @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(*) FROM tj_nao WHERE " + NAO_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>", "</script>"})
    long countNao(@Param("q") EvaluationQueryRequest query);

    @Select("SELECT id, year, month, var_model AS varModel, data FROM tj_nao WHERE id=#{id} AND " + NAO_PREDICATE)
    EvaluationPersistenceRecord getNao(@Param("id") long id);

    @Select("SELECT id, year, month, var_model AS varModel, data FROM tj_nao " +
            "WHERE year=#{year} AND month=#{month} AND var_model=#{varModel} AND " + NAO_PREDICATE + " LIMIT 1")
    EvaluationPersistenceRecord findNaoKey(@Param("year") String year, @Param("month") String month,
                                            @Param("varModel") String varModel);

    @Insert("INSERT INTO tj_nao(year,month,data,var_model) VALUES(#{year},#{month},#{data},#{varModel})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertNao(EvaluationPersistenceRecord record);

    @Update("UPDATE tj_nao SET year=#{year}, month=#{month}, data=#{data}, var_model=#{varModel} " +
            "WHERE id=#{id} AND " + NAO_PREDICATE)
    int updateNao(EvaluationPersistenceRecord record);

    @Delete("DELETE FROM tj_nao WHERE id=#{id} AND " + NAO_PREDICATE)
    int deleteNao(@Param("id") long id);

    @Select({"<script>",
            "SELECT id, year, month, day, var_model AS varModel, data FROM tj_sic WHERE " + SIC_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.day != null'> AND day=#{q.day}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>",
            "ORDER BY CAST(year AS DECIMAL) DESC, CAST(month AS DECIMAL) DESC, " +
                    "CAST(day AS DECIMAL) DESC, id DESC LIMIT #{offset}, #{limit}",
            "</script>"})
    List<EvaluationPersistenceRecord> listSic(@Param("q") EvaluationQueryRequest query,
                                               @Param("offset") int offset, @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(*) FROM tj_sic WHERE " + SIC_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.day != null'> AND day=#{q.day}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>", "</script>"})
    long countSic(@Param("q") EvaluationQueryRequest query);

    @Select("SELECT id, year, month, day, var_model AS varModel, data FROM tj_sic WHERE id=#{id} AND " + SIC_PREDICATE)
    EvaluationPersistenceRecord getSic(@Param("id") long id);

    @Select("SELECT id, year, month, day, var_model AS varModel, data FROM tj_sic WHERE " +
            "year=#{year} AND month=#{month} AND day=#{day} AND var_model=#{varModel} AND " + SIC_PREDICATE + " LIMIT 1")
    EvaluationPersistenceRecord findSicKey(@Param("year") String year, @Param("month") String month,
                                            @Param("day") String day, @Param("varModel") String varModel);

    @Insert("INSERT INTO tj_sic(year,month,day,var_model,data) VALUES(#{year},#{month},#{day},#{varModel},#{data})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSic(EvaluationPersistenceRecord record);

    @Update("UPDATE tj_sic SET year=#{year}, month=#{month}, day=#{day}, var_model=#{varModel}, data=#{data} " +
            "WHERE id=#{id} AND " + SIC_PREDICATE)
    int updateSic(EvaluationPersistenceRecord record);

    @Delete("DELETE FROM tj_sic WHERE id=#{id} AND " + SIC_PREDICATE)
    int deleteSic(@Param("id") long id);

    @Select({"<script>",
            "SELECT id, year, month, var_model AS varModel, data FROM tj_sie WHERE " + SIE_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>",
            "ORDER BY CAST(year AS DECIMAL) DESC, CAST(month AS DECIMAL) DESC, id DESC LIMIT #{offset}, #{limit}",
            "</script>"})
    List<EvaluationPersistenceRecord> listSie(@Param("q") EvaluationQueryRequest query,
                                               @Param("offset") int offset, @Param("limit") int limit);

    @Select({"<script>", "SELECT COUNT(*) FROM tj_sie WHERE " + SIE_PREDICATE,
            "<if test='q.year != null'> AND year=#{q.year}</if>",
            "<if test='q.month != null'> AND month=#{q.month}</if>",
            "<if test='q.varModel != null'> AND var_model=#{q.varModel}</if>", "</script>"})
    long countSie(@Param("q") EvaluationQueryRequest query);

    @Select("SELECT id, year, month, var_model AS varModel, data FROM tj_sie WHERE id=#{id} AND " + SIE_PREDICATE)
    EvaluationPersistenceRecord getSie(@Param("id") long id);

    @Select("SELECT id, year, month, var_model AS varModel, data FROM tj_sie WHERE " +
            "year=#{year} AND month=#{month} AND var_model=#{varModel} AND " + SIE_PREDICATE + " LIMIT 1")
    EvaluationPersistenceRecord findSieKey(@Param("year") String year, @Param("month") String month,
                                            @Param("varModel") String varModel);

    @Insert("INSERT INTO tj_sie(year,month,var_model,data) VALUES(#{year},#{month},#{varModel},#{data})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insertSie(EvaluationPersistenceRecord record);

    @Update("UPDATE tj_sie SET year=#{year}, month=#{month}, var_model=#{varModel}, data=#{data} " +
            "WHERE id=#{id} AND " + SIE_PREDICATE)
    int updateSie(EvaluationPersistenceRecord record);

    @Delete("DELETE FROM tj_sie WHERE id=#{id} AND " + SIE_PREDICATE)
    int deleteSie(@Param("id") long id);
}
