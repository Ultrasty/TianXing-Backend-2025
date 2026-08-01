package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class EcmwfPreviewService {
    private static final List<Integer> VALID_TIMES = Arrays.asList(0, 6, 12, 18);
    private static final List<String> VALID_PROVIDERS = Arrays.asList("ecmwf", "aws", "google", "azure");
    private static final List<String> VALID_MODELS = Arrays.asList("ifs", "aifs-single", "aifs-ens");
    private static final List<String> VALID_TYPES = Arrays.asList("fc", "pf", "em", "es", "ep");
    private static final List<String> VALID_REDUCERS = Arrays.asList("MEAN", "ROW_MEAN", "SAMPLE");
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Pattern DATE = Pattern.compile("^(?:[0-9]{8}|[0-9]{4}-[0-9]{2}-[0-9]{2})$");

    private final ObjectMapper objectMapper;
    private final EvaluationValidator validator;

    @Value("${admin.ecmwf.python:python}")
    private String pythonExecutable;

    @Value("${admin.ecmwf.script:scripts/ecmwf_evaluation_fetch.py}")
    private String scriptPath;

    @Value("${admin.ecmwf.timeout-seconds:240}")
    private long timeoutSeconds;

    @Value("${admin.ecmwf.max-output-bytes:5242880}")
    private long maxOutputBytes;

    public EcmwfPreviewService(ObjectMapper objectMapper, EvaluationValidator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public Map<String, Object> preview(EcmwfPreviewRequest request) {
        EvaluationCategory category = validate(request);
        Path output = null;
        Path log = null;
        try {
            output = Files.createTempFile("tianxing-ecmwf-preview-", ".json");
            log = Files.createTempFile("tianxing-ecmwf-preview-", ".log");
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(request, output));
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(log.toFile());
            Process process = processBuilder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new AdminException("ECMWF_FETCH_TIMEOUT", "ECMWF 数据下载或解析超时",
                        HttpStatus.GATEWAY_TIMEOUT);
            }
            String processLog = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new AdminException("ECMWF_FETCH_FAILED", "ECMWF 数据获取失败: " + truncate(processLog, 1200),
                        HttpStatus.BAD_GATEWAY);
            }
            if (!Files.exists(output) || Files.size(output) == 0 || Files.size(output) > maxOutputBytes) {
                throw new AdminException("ECMWF_OUTPUT_INVALID", "ECMWF 转换结果为空或超过大小限制",
                        HttpStatus.BAD_GATEWAY);
            }

            JsonNode root;
            try (InputStream input = Files.newInputStream(output)) {
                root = objectMapper.readTree(input);
            }
            JsonNode data = root.get("data");
            if (data == null || !data.isArray() || data.size() == 0) {
                throw new AdminException("ECMWF_OUTPUT_INVALID", "ECMWF 转换脚本未返回非空 data 数组",
                        HttpStatus.BAD_GATEWAY);
            }

            EvaluationRecordRequest record = targetRecord(request, data);
            validator.validateAndNormalize(category, record);
            Map<String, Object> recordMap = new LinkedHashMap<>();
            recordMap.put("year", record.getYear());
            if (record.getMonth() != null) recordMap.put("month", record.getMonth());
            if (record.getDay() != null) recordMap.put("day", record.getDay());
            if (record.getVarModel() != null) recordMap.put("varModel", record.getVarModel());
            recordMap.put("data", record.getData());
            recordMap.put("source", "ECMWF");

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("source", "ECMWF");
            result.put("category", category);
            result.put("record", recordMap);
            result.put("metadata", root.get("metadata"));
            return result;
        } catch (AdminException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AdminException("ECMWF_FETCH_FAILED", "ECMWF 获取任务被中断", HttpStatus.BAD_GATEWAY);
        } catch (Exception exception) {
            throw new AdminException("ECMWF_FETCH_FAILED", "ECMWF 获取失败: " + exception.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        } finally {
            deleteQuietly(output);
            deleteQuietly(log);
        }
    }

    private EvaluationCategory validate(EcmwfPreviewRequest request) {
        if (request == null) throw invalid("请求体不能为空");
        EvaluationCategory category = EvaluationCategory.parse(request.getCategory());
        if (!safe(request.getParam())) throw invalid("param 只能包含字母、数字、点、下划线或连字符");
        if (!isBlank(request.getDate()) && !DATE.matcher(request.getDate().trim()).matches()) {
            throw invalid("date 必须是 YYYYMMDD 或 YYYY-MM-DD");
        }
        int time = request.getTime() == null ? 0 : request.getTime();
        if (!VALID_TIMES.contains(time)) throw invalid("time 仅支持 0、6、12、18 UTC");
        int step = request.getStep() == null ? 24 : request.getStep();
        if (step < 0 || step > 360) throw invalid("step 必须在 0 到 360 小时之间");
        String provider = normalized(request.getProvider(), "ecmwf").toLowerCase(Locale.ROOT);
        if (!VALID_PROVIDERS.contains(provider)) throw invalid("provider 不受支持");
        String model = normalized(request.getModel(), "ifs").toLowerCase(Locale.ROOT);
        if (!VALID_MODELS.contains(model)) throw invalid("model 不受支持");
        String type = normalized(request.getForecastType(), "fc").toLowerCase(Locale.ROOT);
        if (!VALID_TYPES.contains(type)) throw invalid("forecastType 不受支持");
        String reducer = normalized(request.getReducer(), "MEAN").toUpperCase(Locale.ROOT);
        if (!VALID_REDUCERS.contains(reducer)) throw invalid("reducer 不受支持");
        int maxPoints = request.getMaxPoints() == null ? 200 : request.getMaxPoints();
        if (maxPoints < 1 || maxPoints > 5000) throw invalid("maxPoints 必须在 1 到 5000 之间");
        if (request.getLevelist() != null && (request.getLevelist() < 1 || request.getLevelist() > 1000)) {
            throw invalid("levelist 必须在 1 到 1000 之间");
        }
        if (!isBlank(request.getLevtype()) && !safe(request.getLevtype())) throw invalid("levtype 格式非法");
        if (!isBlank(request.getStream()) && !safe(request.getStream())) throw invalid("stream 格式非法");

        ArrayNode placeholder = objectMapper.createArrayNode();
        placeholder.add(IntNode.valueOf(0));
        EvaluationRecordRequest target = targetRecord(request, placeholder);
        validator.validateAndNormalize(category, target);
        return category;
    }

    private EvaluationRecordRequest targetRecord(EcmwfPreviewRequest request, JsonNode data) {
        EvaluationRecordRequest record = new EvaluationRecordRequest();
        record.setYear(request.getYear());
        record.setMonth(request.getMonth());
        record.setDay(request.getDay());
        record.setVarModel(request.getVarModel());
        record.setData(data);
        record.setSource("ECMWF");
        return record;
    }

    private List<String> buildCommand(EcmwfPreviewRequest request, Path output) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(resolveScriptPath(scriptPath));
        add(command, "--output", output.toAbsolutePath().toString());
        add(command, "--time", String.valueOf(request.getTime() == null ? 0 : request.getTime()));
        add(command, "--step", String.valueOf(request.getStep() == null ? 24 : request.getStep()));
        add(command, "--param", request.getParam().trim());
        add(command, "--type", normalized(request.getForecastType(), "fc").toLowerCase(Locale.ROOT));
        add(command, "--source", normalized(request.getProvider(), "ecmwf").toLowerCase(Locale.ROOT));
        add(command, "--model", normalized(request.getModel(), "ifs").toLowerCase(Locale.ROOT));
        add(command, "--reducer", normalized(request.getReducer(), "MEAN").toUpperCase(Locale.ROOT));
        add(command, "--max-points", String.valueOf(request.getMaxPoints() == null ? 200 : request.getMaxPoints()));
        if (!isBlank(request.getDate())) add(command, "--date", request.getDate().trim());
        if (!isBlank(request.getLevtype())) add(command, "--levtype", request.getLevtype().trim());
        if (request.getLevelist() != null) add(command, "--levelist", String.valueOf(request.getLevelist()));
        if (!isBlank(request.getStream())) add(command, "--stream", request.getStream().trim());
        return command;
    }

    private void add(List<String> command, String key, String value) {
        command.add(key);
        command.add(value);
    }

    private String resolveScriptPath(String configuredPath) {
        Path direct = Paths.get(configuredPath);
        if (Files.exists(direct)) return direct.toAbsolutePath().toString();
        Path fromRepositoryRoot = Paths.get("demo_backend", configuredPath);
        if (Files.exists(fromRepositoryRoot)) return fromRepositoryRoot.toAbsolutePath().toString();
        return direct.toAbsolutePath().toString();
    }

    private boolean safe(String value) {
        return !isBlank(value) && SAFE_VALUE.matcher(value.trim()).matches();
    }

    private String normalized(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AdminException invalid(String message) {
        return new AdminException("ECMWF_REQUEST_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temporary files are best-effort cleanup only.
        }
    }
}
