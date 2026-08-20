package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class NsidcEvaluationService {
    private final AdminEvaluationMapper mapper;
    private final NsidcEvaluationProcess process;
    private final NsidcEvaluationPublisher publisher;
    private final EvaluationValidator validator;
    private final ObjectMapper objectMapper;

    public NsidcEvaluationService(AdminEvaluationMapper mapper, NsidcEvaluationProcess process,
                                  NsidcEvaluationPublisher publisher, EvaluationValidator validator,
                                  ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.process = process;
        this.publisher = publisher;
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    public JsonNode evaluate(NsidcEvaluationRequest request) {
        if (request == null) throw invalid("请求体不能为空");
        EvaluationCategory category = EvaluationCategory.parse(request.getCategory());
        if (category != EvaluationCategory.SIC && category != EvaluationCategory.SIE) {
            throw invalid("NSIDC 自动评估仅支持 SIC 或 SIE");
        }
        String mode = normalizeMode(request.getMode());
        ObjectNode input = category == EvaluationCategory.SIC ? sicInput(request) : sieInput(request);
        JsonNode result = process.execute(input);
        validateResult(category, result);
        ObjectNode response = ((ObjectNode) result).deepCopy();
        if ("UPSERT".equals(mode)) {
            response.set("publication", objectMapper.valueToTree(publisher.publish(category, result)));
            response.put("published", true);
        } else {
            response.put("published", false);
        }
        return response;
    }

    private ObjectNode sicInput(NsidcEvaluationRequest request) {
        LocalDate date = parseDate(request.getYear(), request.getMonth(), request.getDay());
        int offset = request.getLeadStartOffsetDays() == null ? 0 : request.getLeadStartOffsetDays();
        if (offset != 0 && offset != 1) throw invalid("leadStartOffsetDays 只能是 0 或 1");
        EvaluationPersistenceRecord prediction = mapper.findSicPrediction(
                String.valueOf(date.getYear()), String.valueOf(date.getMonthValue()), String.valueOf(date.getDayOfMonth()));
        if (prediction == null) {
            throw new AdminException("NSIDC_PREDICTION_NOT_FOUND", "所选日期不存在 SIC_Ice-BCNet 预测值",
                    HttpStatus.NOT_FOUND);
        }
        SicGridRecord grid = mapper.getSicGrid();
        if (grid == null || grid.getLat() == null || grid.getLon() == null) {
            throw new AdminException("NSIDC_GRID_NOT_FOUND", "info_sic_latlon 缺少 Ice-BCNet 网格经纬度",
                    HttpStatus.CONFLICT);
        }
        ObjectNode input = objectMapper.createObjectNode();
        input.put("mode", "SIC");
        input.put("year", date.getYear());
        input.put("month", date.getMonthValue());
        input.put("day", date.getDayOfMonth());
        input.put("leadStartOffsetDays", offset);
        input.set("prediction", parseDatabaseJson(prediction.getData(), "SIC_Ice-BCNet"));
        input.set("latitude", parseDatabaseJson(grid.getLat(), "info_sic_latlon.lat"));
        input.set("longitude", parseDatabaseJson(grid.getLon(), "info_sic_latlon.lon"));
        return input;
    }

    private ObjectNode sieInput(NsidcEvaluationRequest request) {
        String year = normalizeYear(request.getYear());
        List<EvaluationPersistenceRecord> predictions = mapper.listSiePredictions(year);
        if (predictions == null || predictions.size() < 2) {
            throw new AdminException("NSIDC_PREDICTION_NOT_FOUND",
                    "所选年份至少需要 2 个 prediction_IceTFT 月起报样本", HttpStatus.NOT_FOUND);
        }
        ObjectNode input = objectMapper.createObjectNode();
        input.put("mode", "SIE");
        input.put("year", Integer.parseInt(year));
        ArrayNode rows = input.putArray("predictions");
        for (EvaluationPersistenceRecord prediction : predictions) {
            ObjectNode row = rows.addObject();
            row.put("year", prediction.getYear());
            row.put("month", prediction.getMonth());
            row.set("data", parseDatabaseJson(prediction.getData(), "prediction_IceTFT"));
        }
        return input;
    }

    private void validateResult(EvaluationCategory category, JsonNode result) {
        if (result == null || !result.isObject() || !"NSIDC".equals(result.path("source").asText()) ||
                !"EVALUATION_METRIC".equals(result.path("dataKind").asText()) ||
                !category.name().equals(result.path("category").asText())) {
            throw invalidOutput("NSIDC 脚本返回的来源、类别或数据类型不正确");
        }
        JsonNode records = result.get("records");
        int expected = category == EvaluationCategory.SIC ? 2 : 6;
        if (records == null || !records.isArray() || records.size() != expected) {
            throw invalidOutput("NSIDC 脚本返回的指标记录数量不正确");
        }
        for (JsonNode node : records) {
            try {
                EvaluationRecordRequest record = objectMapper.treeToValue(node, EvaluationRecordRequest.class);
                if (!"NSIDC".equals(record.getSource())) throw invalidOutput("NSIDC 指标记录缺少来源");
                validator.validateAndNormalize(category, record);
            } catch (AdminException exception) {
                throw exception;
            } catch (Exception exception) {
                throw invalidOutput("NSIDC 指标记录格式不正确");
            }
        }
        JsonNode observation = result.get("observation");
        if (observation == null || observation.path("datasetId").asText().isEmpty() ||
                observation.path("version").asText().isEmpty() || observation.path("sha256").isMissingNode()) {
            throw invalidOutput("NSIDC 结果缺少观测产品版本或文件校验信息");
        }
    }

    private JsonNode parseDatabaseJson(String value, String label) {
        try {
            JsonNode node = objectMapper.readTree(value);
            if (node == null || !node.isArray()) throw new IllegalArgumentException();
            return node;
        } catch (Exception exception) {
            throw new AdminException("NSIDC_INPUT_INVALID", label + " 不是有效 JSON 数组",
                    HttpStatus.CONFLICT);
        }
    }

    private LocalDate parseDate(String year, String month, String day) {
        try {
            return LocalDate.of(Integer.parseInt(normalizeYear(year)), Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException | NumberFormatException | NullPointerException exception) {
            throw invalid("SIC 年、月、日不是有效日期");
        }
    }

    private String normalizeYear(String year) {
        if (year == null || !year.matches("^[0-9]{4}$")) throw invalid("year 必须是四位数字");
        int number = Integer.parseInt(year);
        if (number < 2012 || number > 2100) throw invalid("year 必须在 2012 到 2100 之间");
        return String.valueOf(number);
    }

    private String normalizeMode(String mode) {
        String normalized = mode == null ? "PREVIEW" : mode.trim().toUpperCase(Locale.ROOT);
        if (!"PREVIEW".equals(normalized) && !"UPSERT".equals(normalized)) {
            throw invalid("mode 只能是 PREVIEW 或 UPSERT");
        }
        return normalized;
    }

    private AdminException invalid(String message) {
        return new AdminException("NSIDC_REQUEST_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    private AdminException invalidOutput(String message) {
        return new AdminException("NSIDC_OUTPUT_INVALID", message, HttpStatus.BAD_GATEWAY);
    }
}
