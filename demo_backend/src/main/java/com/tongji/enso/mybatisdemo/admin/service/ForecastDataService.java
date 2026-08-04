package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.dto.ForecastDataRequest;
import com.tongji.enso.mybatisdemo.admin.model.ForecastDataset;
import com.tongji.enso.mybatisdemo.admin.repository.ForecastDataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastDataService {
    private final ForecastDataRepository repository;
    private final ObjectMapper objectMapper;

    public ForecastDataService(ForecastDataRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> meta() {
        List<Map<String, Object>> datasets = new ArrayList<Map<String, Object>>();
        for (ForecastDataset dataset : ForecastDataset.values()) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", dataset.name());
            item.put("table", dataset.getTableName());
            item.put("models", dataset.getForecastModels());
            datasets.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("datasets", datasets);
        return result;
    }

    public Map<String, Object> page(String datasetValue,
                                    String year,
                                    String month,
                                    String varModel,
                                    Integer page,
                                    Integer pageSize) {
        ForecastDataset dataset = parseDataset(datasetValue);
        int normalizedPage = page == null ? 1 : Math.max(page, 1);
        int normalizedPageSize = pageSize == null ? 20 : Math.min(Math.max(pageSize, 1), 100);
        if (!isBlank(varModel) && !dataset.supportsModel(varModel.trim())) {
            throw badRequest("该 var_model 不属于 " + dataset.name() + " 的预报数据范围");
        }
        return repository.page(dataset, trimToNull(year), normalizeMonthOrNull(month), trimToNull(varModel), normalizedPage, normalizedPageSize);
    }

    public Map<String, Object> findOne(String datasetValue, long id) {
        ForecastDataset dataset = parseDataset(datasetValue);
        Map<String, Object> record = repository.findById(dataset, id);
        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预报数据不存在，或该记录不属于本模块维护范围");
        }
        return record;
    }

    public Map<String, Object> create(ForecastDataRequest request) {
        NormalizedForecastData data = validateAndNormalize(request);
        if (repository.existsNaturalKey(data.dataset, data.year, data.month, data.varModel, null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同 year/month/var_model 的预报数据已存在，请使用更新功能");
        }
        long id = repository.insert(data.dataset, data.year, data.month, data.varModel, data.data);
        Map<String, Object> result = repository.findById(data.dataset, id);
        if (result == null) {
            result = new LinkedHashMap<String, Object>();
            result.put("id", id);
        }
        return result;
    }

    public Map<String, Object> update(long id, ForecastDataRequest request) {
        NormalizedForecastData data = validateAndNormalize(request);
        Map<String, Object> existing = repository.findById(data.dataset, id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预报数据不存在，或该记录属于评估数据而不在 2.1-2.3 的维护范围内");
        }
        if (repository.existsNaturalKey(data.dataset, data.year, data.month, data.varModel, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "更新后会与已有 year/month/var_model 记录重复");
        }
        repository.update(data.dataset, id, data.year, data.month, data.varModel, data.data);
        return repository.findById(data.dataset, id);
    }

    public void delete(String datasetValue, long id) {
        ForecastDataset dataset = parseDataset(datasetValue);
        Map<String, Object> existing = repository.findById(dataset, id);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "预报数据不存在，或该记录不属于可维护的预报数据");
        }
        if (repository.delete(dataset, id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "删除失败：记录不存在");
        }
    }

    public Map<String, Object> upload(String dataset,
                                      String year,
                                      String month,
                                      String varModel,
                                      MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要上传的 JSON 文件");
        }
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            ForecastDataRequest request = new ForecastDataRequest();
            request.setDataset(dataset);
            request.setYear(year);
            request.setMonth(month);
            request.setVarModel(varModel);
            request.setData(content);
            return create(request);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("读取上传文件失败: " + ex.getMessage());
        }
    }

    /** 在下载 ECMWF 数据前先验证目标数据集、年月和 var_model，避免无效请求触发昂贵下载。 */
    public void validateTarget(String datasetValue, String yearValue, String monthValue, String varModelValue) {
        ForecastDataset dataset = parseDataset(datasetValue);
        normalizeYear(yearValue);
        normalizeMonth(monthValue);
        String varModel = requireText(varModelValue, "varModel");
        if (!dataset.supportsModel(varModel)) {
            throw badRequest("var_model=" + varModel + " 不属于 " + dataset.name() + " 的预报结果数据");
        }
    }

    /** ECMWF 导入服务使用同一校验和入库入口。支持传入 overwrite 二次确认覆盖 */
    public Map<String, Object> createFromDecodedJson(String dataset,
                                                      String year,
                                                      String month,
                                                      String varModel,
                                                      String dataJson,
                                                      Boolean overwrite) {
        ForecastDataRequest request = new ForecastDataRequest();
        request.setDataset(dataset);
        request.setYear(year);
        request.setMonth(month);
        request.setVarModel(varModel);
        request.setData(dataJson);

        NormalizedForecastData normalized = validateAndNormalize(request);
        Map<String, Object> existing = repository.findByNaturalKey(normalized.dataset, normalized.year, normalized.month, normalized.varModel);
        if (existing != null) {
            if (Boolean.TRUE.equals(overwrite)) {
                long existingId = ((Number) existing.get("id")).longValue();
                return update(existingId, request);
            } else {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "相同 year/month/var_model 的预报数据已存在，请确认是否覆盖");
            }
        }
        return create(request);
    }

    private NormalizedForecastData validateAndNormalize(ForecastDataRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        ForecastDataset dataset = parseDataset(request.getDataset());
        String year = normalizeYear(request.getYear());
        String month = normalizeMonth(request.getMonth());
        String varModel = requireText(request.getVarModel(), "varModel");
        if (!dataset.supportsModel(varModel)) {
            throw badRequest("var_model=" + varModel + " 不属于 " + dataset.name() + " 的预报结果数据；为避免与评估数据冲突，本接口拒绝修改");
        }
        String data = requireText(request.getData(), "data");
        validateJsonArray(data);
        return new NormalizedForecastData(dataset, year, month, varModel, data);
    }

    private void validateJsonArray(String data) {
        try {
            JsonNode node = objectMapper.readTree(data);
            if (node == null || !node.isArray()) {
                throw badRequest("data 必须是 JSON 数组，例如 [1.0,2.0] 或 [[...],[...]]");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw badRequest("data 不是合法 JSON: " + ex.getMessage());
        }
    }

    private ForecastDataset parseDataset(String value) {
        try {
            return ForecastDataset.from(value);
        } catch (IllegalArgumentException ex) {
            throw badRequest(ex.getMessage());
        }
    }

    private String normalizeYear(String value) {
        String year = requireText(value, "year");
        if (!year.matches("\\d{4}")) {
            throw badRequest("year 必须是四位年份");
        }
        return year;
    }

    private String normalizeMonth(String value) {
        String month = requireText(value, "month");
        try {
            int m = Integer.parseInt(month);
            if (m < 1 || m > 12) {
                throw badRequest("month 必须在 1-12 之间");
            }
            return String.valueOf(m);
        } catch (NumberFormatException ex) {
            throw badRequest("month 必须是 1-12 的整数");
        }
    }

    private String normalizeMonthOrNull(String value) {
        return isBlank(value) ? null : normalizeMonth(value);
    }

    private String requireText(String value, String field) {
        if (isBlank(value)) {
            throw badRequest(field + " 不能为空");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static final class NormalizedForecastData {
        private final ForecastDataset dataset;
        private final String year;
        private final String month;
        private final String varModel;
        private final String data;

        private NormalizedForecastData(ForecastDataset dataset,
                                       String year,
                                       String month,
                                       String varModel,
                                       String data) {
            this.dataset = dataset;
            this.year = year;
            this.month = month;
            this.varModel = varModel;
            this.data = data;
        }
    }
}
