package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.dto.ForecastDataRequest;
import com.tongji.enso.mybatisdemo.admin.model.ForecastDataset;
import com.tongji.enso.mybatisdemo.admin.repository.ForecastDataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ForecastDataService {

    private static final String ENSO_ASC = "nino34_asc";
    private static final String ENSO_GTC = "nino34_gtc";
    private static final String ENSO_MC = "nino34_mc";
    private static final String ENSO_MEAN = "nino34_mean";

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

    /**
     * 新增预报数据。
     *
     * ENSO 的 ASC/GTC/MC 任意一种发生变化后，
     * 会自动检查同年月三种基础模型是否齐全：
     * - 齐全：计算 nino34_mean 并 INSERT/UPDATE
     * - 不齐全：不生成 mean
     */
    @Transactional
    public Map<String, Object> create(ForecastDataRequest request) {
        NormalizedForecastData data = validateAndNormalize(request);

        if (repository.existsNaturalKey(
                data.dataset,
                data.year,
                data.month,
                data.varModel,
                null
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "相同 year/month/var_model 的预报数据已存在，请使用更新功能"
            );
        }

        long id = repository.insert(
                data.dataset,
                data.year,
                data.month,
                data.varModel,
                data.data
        );

        // 只有基础模型发生变化时才同步 mean。
        if (isEnsoBaseModel(data.dataset, data.varModel)) {
            syncEnsoMean(data.year, data.month);
        }

        Map<String, Object> result =
                repository.findById(data.dataset, id);

        if (result == null) {
            result = new LinkedHashMap<String, Object>();
            result.put("id", id);
        }

        return result;
    }

    /**
     * 更新预报数据。
     *
     * 如果修改了 ENSO 基础模型的 year/month/var_model，
     * 旧年月和新年月都会重新同步 mean，避免留下失效的 mean。
     */
    @Transactional
    public Map<String, Object> update(long id, ForecastDataRequest request) {
        NormalizedForecastData data = validateAndNormalize(request);

        Map<String, Object> existing =
                repository.findById(data.dataset, id);

        if (existing == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "预报数据不存在，或该记录属于评估数据而不在 2.1-2.3 的维护范围内"
            );
        }

        if (repository.existsNaturalKey(
                data.dataset,
                data.year,
                data.month,
                data.varModel,
                id
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "更新后会与已有 year/month/var_model 记录重复"
            );
        }

        String oldYear = valueAsString(existing.get("year"));
        String oldMonth = valueAsString(existing.get("month"));
        String oldVarModel = valueAsString(existing.get("var_model"));

        repository.update(
                data.dataset,
                id,
                data.year,
                data.month,
                data.varModel,
                data.data
        );

        boolean oldIsEnsoBase =
                isEnsoBaseModel(
                        data.dataset,
                        oldVarModel
                );

        boolean newIsEnsoBase =
                isEnsoBaseModel(
                        data.dataset,
                        data.varModel
                );

        boolean sameMonth =
                data.year.equals(oldYear)
                        && data.month.equals(oldMonth);

        // update 已经完成，此时同步旧年月会读取更新后的数据库状态。
        if (oldIsEnsoBase) {
            syncEnsoMean(
                    oldYear,
                    oldMonth
            );
        }

        // 如果新基础模型移动到了另一个年月，
        // 还需要额外同步新年月。
        if (newIsEnsoBase
                && (!oldIsEnsoBase || !sameMonth)) {

            syncEnsoMean(
                    data.year,
                    data.month
            );
        }

        return repository.findById(data.dataset, id);
    }

    /**
     * 删除预报数据。
     *
     * 删除 ENSO ASC/GTC/MC 任意一条后，
     * 如果该年月不再具备三种基础模型，则自动删除对应 mean。
     */
    @Transactional
    public void delete(String datasetValue, long id) {
        ForecastDataset dataset = parseDataset(datasetValue);

        Map<String, Object> existing =
                repository.findById(dataset, id);

        if (existing == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "预报数据不存在，或该记录不属于可维护的预报数据"
            );
        }

        String oldYear = valueAsString(existing.get("year"));
        String oldMonth = valueAsString(existing.get("month"));
        String oldVarModel = valueAsString(existing.get("var_model"));

        if (repository.delete(dataset, id) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "删除失败：记录不存在"
            );
        }

        if (isEnsoBaseModel(dataset, oldVarModel)) {
            syncEnsoMean(oldYear, oldMonth);
        }
    }

    @Transactional
    public Map<String, Object> upload(String dataset,
                                      String year,
                                      String month,
                                      String varModel,
                                      MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw badRequest("请选择要上传的 JSON 文件");
        }

        try {
            String content =
                    new String(
                            file.getBytes(),
                            StandardCharsets.UTF_8
                    );

            ForecastDataRequest request =
                    new ForecastDataRequest();

            request.setDataset(dataset);
            request.setYear(year);
            request.setMonth(month);
            request.setVarModel(varModel);
            request.setData(content);

            return create(request);

        } catch (ResponseStatusException ex) {
            throw ex;

        } catch (Exception ex) {
            throw badRequest(
                    "读取上传文件失败: "
                            + ex.getMessage()
            );
        }
    }

    /** 在下载 ECMWF 数据前先验证目标数据集、年月和 var_model，避免无效请求触发昂贵下载。 */
    public void validateTarget(String datasetValue,
                               String yearValue,
                               String monthValue,
                               String varModelValue) {

        ForecastDataset dataset =
                parseDataset(datasetValue);

        normalizeYear(yearValue);
        normalizeMonth(monthValue);

        String varModel =
                requireText(
                        varModelValue,
                        "varModel"
                );

        if (!dataset.supportsModel(varModel)) {
            throw badRequest(
                    "var_model="
                            + varModel
                            + " 不属于 "
                            + dataset.name()
                            + " 的预报结果数据"
            );
        }
    }

    /** ECMWF / NOAA 导入服务使用同一校验和入库入口。支持 overwrite 覆盖。 */
    @Transactional
    public Map<String, Object> createFromDecodedJson(String dataset,
                                                     String year,
                                                     String month,
                                                     String varModel,
                                                     String dataJson,
                                                     Boolean overwrite) {

        ForecastDataRequest request =
                new ForecastDataRequest();

        request.setDataset(dataset);
        request.setYear(year);
        request.setMonth(month);
        request.setVarModel(varModel);
        request.setData(dataJson);

        NormalizedForecastData normalized =
                validateAndNormalize(request);

        Map<String, Object> existing =
                repository.findByNaturalKey(
                        normalized.dataset,
                        normalized.year,
                        normalized.month,
                        normalized.varModel
                );

        if (existing != null) {

            if (Boolean.TRUE.equals(overwrite)) {

                long existingId =
                        ((Number) existing.get("id"))
                                .longValue();

                return update(
                        existingId,
                        request
                );

            } else {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "相同 year/month/var_model 的预报数据已存在，请确认是否覆盖"
                );
            }
        }

        return create(request);
    }

    /**
     * 一次性重建历史 ENSO mean。
     *
     * 会扫描 tj_enso 中所有 ENSO 预测年月：
     * - ASC/GTC/MC 三种齐全 -> INSERT/UPDATE nino34_mean
     * - 三种不齐全但存在旧 mean -> 删除旧 mean
     *
     * 用于修复历史数据。
     */
    @Transactional
    public Map<String, Object> rebuildEnsoMeans() {

        List<Map<String, Object>> yearMonths =
                repository.findDistinctYearMonths(
                        ForecastDataset.ENSO,
                        ForecastDataset.ENSO.getForecastModels()
                );

        int processed = 0;
        int upserted = 0;
        int deleted = 0;
        int unchanged = 0;

        for (Map<String, Object> item : yearMonths) {

            String year =
                    valueAsString(
                            item.get("year")
                    );

            String month =
                    valueAsString(
                            item.get("month")
                    );

            MeanSyncAction action =
                    syncEnsoMean(
                            year,
                            month
                    );

            processed++;

            if (action == MeanSyncAction.UPSERTED) {
                upserted++;
            } else if (action == MeanSyncAction.DELETED) {
                deleted++;
            } else {
                unchanged++;
            }
        }

        Map<String, Object> result =
                new LinkedHashMap<String, Object>();

        result.put("processedMonths", processed);
        result.put("upsertedMeans", upserted);
        result.put("deletedMeans", deleted);
        result.put("unchangedMonths", unchanged);

        return result;
    }

    /**
     * 同步指定年月的 nino34_mean。
     *
     * 规则：
     * 1. ASC/GTC/MC 全部存在且均为非空一维数值数组：
     *    mean[i] = (ASC[i] + GTC[i] + MC[i]) / 3
     *
     * 2. 三条序列长度不同时，沿用前台旧逻辑：
     *    取三者最短长度。
     *
     * 3. 基础模型不齐全：
     *    如果数据库已有 mean，则删除，防止 stale mean。
     */
    private MeanSyncAction syncEnsoMean(
            String year,
            String month
    ) {

        if (isBlank(year) || isBlank(month)) {
            return MeanSyncAction.NONE;
        }

        Map<String, Object> ascRecord =
                repository.findByNaturalKey(
                        ForecastDataset.ENSO,
                        year,
                        month,
                        ENSO_ASC
                );

        Map<String, Object> gtcRecord =
                repository.findByNaturalKey(
                        ForecastDataset.ENSO,
                        year,
                        month,
                        ENSO_GTC
                );

        Map<String, Object> mcRecord =
                repository.findByNaturalKey(
                        ForecastDataset.ENSO,
                        year,
                        month,
                        ENSO_MC
                );

        Map<String, Object> meanRecord =
                repository.findByNaturalKey(
                        ForecastDataset.ENSO,
                        year,
                        month,
                        ENSO_MEAN
                );

        // 三种基础模型不齐全：mean 不应继续存在。
        if (ascRecord == null
                || gtcRecord == null
                || mcRecord == null) {

            if (meanRecord != null) {
                long meanId =
                        ((Number) meanRecord.get("id"))
                                .longValue();

                repository.delete(
                        ForecastDataset.ENSO,
                        meanId
                );

                return MeanSyncAction.DELETED;
            }

            return MeanSyncAction.NONE;
        }

        List<Double> asc =
                parseEnsoSeries(
                        ascRecord,
                        ENSO_ASC
                );

        List<Double> gtc =
                parseEnsoSeries(
                        gtcRecord,
                        ENSO_GTC
                );

        List<Double> mc =
                parseEnsoSeries(
                        mcRecord,
                        ENSO_MC
                );

        if (asc.isEmpty()
                || gtc.isEmpty()
                || mc.isEmpty()) {

            if (meanRecord != null) {
                long meanId =
                        ((Number) meanRecord.get("id"))
                                .longValue();

                repository.delete(
                        ForecastDataset.ENSO,
                        meanId
                );

                return MeanSyncAction.DELETED;
            }

            return MeanSyncAction.NONE;
        }

        int size =
                Math.min(
                        asc.size(),
                        Math.min(
                                gtc.size(),
                                mc.size()
                        )
                );

        List<Double> mean =
                new ArrayList<Double>(
                        size
                );

        for (int i = 0; i < size; i++) {

            mean.add(
                    (
                            asc.get(i)
                                    + gtc.get(i)
                                    + mc.get(i)
                    ) / 3.0
            );
        }

        String meanJson;

        try {
            meanJson =
                    objectMapper.writeValueAsString(
                            mean
                    );
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "生成 nino34_mean JSON 失败: "
                            + ex.getMessage()
            );
        }

        if (meanRecord == null) {

            repository.insert(
                    ForecastDataset.ENSO,
                    year,
                    month,
                    ENSO_MEAN,
                    meanJson
            );

        } else {

            long meanId =
                    ((Number) meanRecord.get("id"))
                            .longValue();

            repository.update(
                    ForecastDataset.ENSO,
                    meanId,
                    year,
                    month,
                    ENSO_MEAN,
                    meanJson
            );
        }

        return MeanSyncAction.UPSERTED;
    }

    /**
     * ENSO 三种基础模型必须是一维数值数组。
     */
    private List<Double> parseEnsoSeries(
            Map<String, Object> record,
            String varModel
    ) {

        String data =
                valueAsDataString(
                        record.get("data")
                );

        if (isBlank(data)) {
            return new ArrayList<Double>();
        }

        try {

            JsonNode node =
                    objectMapper.readTree(data);

            if (node == null || !node.isArray()) {
                throw badRequest(
                        varModel
                                + " 的 data 必须是一维 JSON 数组"
                );
            }

            List<Double> values =
                    new ArrayList<Double>();

            for (JsonNode item : node) {

                if (!item.isNumber()) {
                    throw badRequest(
                            varModel
                                    + " 的 data 必须是一维数值数组，不能包含对象、字符串或嵌套数组"
                    );
                }

                values.add(
                        item.asDouble()
                );
            }

            return values;

        } catch (ResponseStatusException ex) {
            throw ex;

        } catch (Exception ex) {
            throw badRequest(
                    varModel
                            + " 的 data 解析失败: "
                            + ex.getMessage()
            );
        }
    }

    private boolean isEnsoBaseModel(
            ForecastDataset dataset,
            String varModel
    ) {

        if (dataset != ForecastDataset.ENSO
                || varModel == null) {
            return false;
        }

        return ENSO_ASC.equals(varModel)
                || ENSO_GTC.equals(varModel)
                || ENSO_MC.equals(varModel);
    }

    private NormalizedForecastData validateAndNormalize(
            ForecastDataRequest request
    ) {

        if (request == null) {
            throw badRequest(
                    "请求体不能为空"
            );
        }

        ForecastDataset dataset =
                parseDataset(
                        request.getDataset()
                );

        String year =
                normalizeYear(
                        request.getYear()
                );

        String month =
                normalizeMonth(
                        request.getMonth()
                );

        String varModel =
                requireText(
                        request.getVarModel(),
                        "varModel"
                );

        if (!dataset.supportsModel(varModel)) {
            throw badRequest(
                    "var_model="
                            + varModel
                            + " 不属于 "
                            + dataset.name()
                            + " 的预报结果数据；为避免与评估数据冲突，本接口拒绝修改"
            );
        }

        String data =
                requireText(
                        request.getData(),
                        "data"
                );

        validateJsonArray(data);

        return new NormalizedForecastData(
                dataset,
                year,
                month,
                varModel,
                data
        );
    }

    private void validateJsonArray(String data) {
        try {

            JsonNode node =
                    objectMapper.readTree(data);

            if (node == null || !node.isArray()) {
                throw badRequest(
                        "data 必须是 JSON 数组，例如 [1.0,2.0] 或 [[...],[...]]"
                );
            }

        } catch (ResponseStatusException ex) {
            throw ex;

        } catch (Exception ex) {
            throw badRequest(
                    "data 不是合法 JSON: "
                            + ex.getMessage()
            );
        }
    }

    private ForecastDataset parseDataset(
            String value
    ) {

        try {
            return ForecastDataset.from(value);

        } catch (IllegalArgumentException ex) {
            throw badRequest(
                    ex.getMessage()
            );
        }
    }

    private String normalizeYear(
            String value
    ) {

        String year =
                requireText(
                        value,
                        "year"
                );

        if (!year.matches("\\d{4}")) {
            throw badRequest(
                    "year 必须是四位年份"
            );
        }

        return year;
    }

    private String normalizeMonth(
            String value
    ) {

        String month =
                requireText(
                        value,
                        "month"
                );

        try {

            int m =
                    Integer.parseInt(month);

            if (m < 1 || m > 12) {
                throw badRequest(
                        "month 必须在 1-12 之间"
                );
            }

            return String.valueOf(m);

        } catch (NumberFormatException ex) {
            throw badRequest(
                    "month 必须是 1-12 的整数"
            );
        }
    }

    private String normalizeMonthOrNull(
            String value
    ) {

        return isBlank(value)
                ? null
                : normalizeMonth(value);
    }

    private String requireText(
            String value,
            String field
    ) {

        if (isBlank(value)) {
            throw badRequest(
                    field + " 不能为空"
            );
        }

        return value.trim();
    }

    private String trimToNull(
            String value
    ) {

        return isBlank(value)
                ? null
                : value.trim();
    }

    private boolean isBlank(
            String value
    ) {

        return value == null
                || value.trim().isEmpty();
    }

    private String valueAsString(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        return String.valueOf(value);
    }

    private String valueAsDataString(
            Object value
    ) {

        if (value == null) {
            return null;
        }

        if (value instanceof byte[]) {
            return new String(
                    (byte[]) value,
                    StandardCharsets.UTF_8
            );
        }

        return String.valueOf(value);
    }

    private ResponseStatusException badRequest(
            String message
    ) {

        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }

    private enum MeanSyncAction {
        UPSERTED,
        DELETED,
        NONE
    }

    private static final class NormalizedForecastData {

        private final ForecastDataset dataset;
        private final String year;
        private final String month;
        private final String varModel;
        private final String data;

        private NormalizedForecastData(
                ForecastDataset dataset,
                String year,
                String month,
                String varModel,
                String data
        ) {
            this.dataset = dataset;
            this.year = year;
            this.month = month;
            this.varModel = varModel;
            this.data = data;
        }
    }
}
