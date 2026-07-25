package com.tongji.enso.mybatisdemo.admin.repository;

import com.tongji.enso.mybatisdemo.admin.model.ForecastDataset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ForecastDataRepository {
    private final JdbcTemplate jdbcTemplate;

    public ForecastDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> page(ForecastDataset dataset,
                                    String year,
                                    String month,
                                    String varModel,
                                    int page,
                                    int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE `var_model` IN (");
        where.append(placeholders(dataset.getForecastModels().size())).append(")");
        List<Object> params = new ArrayList<Object>(dataset.getForecastModels());

        if (!isBlank(year)) {
            where.append(" AND `year` = ?");
            params.add(year.trim());
        }
        if (!isBlank(month)) {
            where.append(" AND `month` = ?");
            params.add(month.trim());
        }
        if (!isBlank(varModel)) {
            where.append(" AND `var_model` = ?");
            params.add(varModel.trim());
        }

        String table = dataset.getTableName();
        Integer total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + table + "`" + where,
                params.toArray(),
                Integer.class
        );

        List<Object> pageParams = new ArrayList<Object>(params);
        pageParams.add(pageSize);
        pageParams.add((page - 1) * pageSize);
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT `id`, `year`, `month`, `var_model`, " +
                        "LEFT(CAST(`data` AS CHAR), 300) AS `data_preview`, " +
                        "CHAR_LENGTH(CAST(`data` AS CHAR)) AS `data_length` " +
                        "FROM `" + table + "`" + where +
                        " ORDER BY CAST(`year` AS UNSIGNED) DESC, CAST(`month` AS UNSIGNED) DESC, `id` DESC " +
                        "LIMIT ? OFFSET ?",
                pageParams.toArray()
        );

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("items", items);
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public Map<String, Object> findById(ForecastDataset dataset, long id) {
        List<Object> params = new ArrayList<Object>();
        params.add(id);
        params.addAll(dataset.getForecastModels());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT `id`, `year`, `month`, `var_model`, `data` FROM `" + dataset.getTableName() + "` " +
                        "WHERE `id` = ? AND `var_model` IN (" + placeholders(dataset.getForecastModels().size()) + ") LIMIT 1",
                params.toArray()
        );
        return rows.isEmpty() ? null : rows.get(0);
    }

    public boolean existsNaturalKey(ForecastDataset dataset,
                                    String year,
                                    String month,
                                    String varModel,
                                    Long excludeId) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM `" + dataset.getTableName() + "` WHERE `year` = ? AND `month` = ? AND `var_model` = ?"
        );
        List<Object> params = new ArrayList<Object>();
        params.add(year);
        params.add(month);
        params.add(varModel);
        if (excludeId != null) {
            sql.append(" AND `id` <> ?");
            params.add(excludeId);
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), params.toArray(), Integer.class);
        return count != null && count > 0;
    }

    public long insert(ForecastDataset dataset,
                       String year,
                       String month,
                       String varModel,
                       String data) {
        final String sql = "INSERT INTO `" + dataset.getTableName() + "` (`year`, `month`, `var_model`, `data`) VALUES (?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, year);
            ps.setString(2, month);
            ps.setString(3, varModel);
            ps.setString(4, data);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key == null ? -1L : key.longValue();
    }

    public int update(ForecastDataset dataset,
                      long id,
                      String year,
                      String month,
                      String varModel,
                      String data) {
        List<Object> params = new ArrayList<Object>();
        params.add(year);
        params.add(month);
        params.add(varModel);
        params.add(data);
        params.add(id);
        params.addAll(dataset.getForecastModels());
        return jdbcTemplate.update(
                "UPDATE `" + dataset.getTableName() + "` SET `year` = ?, `month` = ?, `var_model` = ?, `data` = ? " +
                        "WHERE `id` = ? AND `var_model` IN (" + placeholders(dataset.getForecastModels().size()) + ")",
                params.toArray()
        );
    }

    public int delete(ForecastDataset dataset, long id) {
        List<Object> params = new ArrayList<Object>();
        params.add(id);
        params.addAll(dataset.getForecastModels());
        return jdbcTemplate.update(
                "DELETE FROM `" + dataset.getTableName() + "` WHERE `id` = ? AND `var_model` IN (" +
                        placeholders(dataset.getForecastModels().size()) + ")",
                params.toArray()
        );
    }

    private String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
