package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.dto.EcmwfImportRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class EcmwfImportService {
    private static final List<Integer> VALID_TIMES = Arrays.asList(0, 6, 12, 18);
    private static final List<String> VALID_SOURCES = Arrays.asList("ecmwf", "aws", "google", "azure");
    private static final List<String> VALID_MODELS = Arrays.asList("ifs", "aifs-single", "aifs-ens");
    private static final List<String> VALID_TYPES = Arrays.asList("fc", "pf", "em", "es", "ep");

    private final ForecastDataService forecastDataService;
    private final ObjectMapper objectMapper;

    @Value("${admin.ecmwf.python:python3}")
    private String pythonExecutable;

    @Value("${admin.ecmwf.script:scripts/ecmwf_fetch.py}")
    private String scriptPath;

    @Value("${admin.ecmwf.timeout-seconds:240}")
    private long timeoutSeconds;

    public EcmwfImportService(ForecastDataService forecastDataService, ObjectMapper objectMapper) {
        this.forecastDataService = forecastDataService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> importForecast(EcmwfImportRequest request) {
        validate(request);
        Path output = null;
        try {
            output = Files.createTempFile("tianxing-ecmwf-", ".json");
            List<String> command = buildCommand(request, output);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Path processLogFile = Files.createTempFile("tianxing-ecmwf-", ".log");
            processBuilder.redirectOutput(processLogFile.toFile());
            Process process = processBuilder.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                Files.deleteIfExists(processLogFile);
                throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "ECMWF 数据下载/解析超时");
            }
            String processLog = new String(Files.readAllBytes(processLogFile), StandardCharsets.UTF_8);
            Files.deleteIfExists(processLogFile);
            if (process.exitValue() != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "ECMWF 数据获取失败: " + truncate(processLog, 1500));
            }

            String json = new String(Files.readAllBytes(output), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ECMWF 解析脚本没有返回合法 data 数组");
            }

            Map<String, Object> inserted = forecastDataService.createFromDecodedJson(
                    request.getDataset(),
                    request.getYear(),
                    request.getMonth(),
                    request.getVarModel(),
                    objectMapper.writeValueAsString(dataNode)
            );

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("record", inserted);
            result.put("ecmwf", objectMapper.convertValue(root.get("metadata"), Map.class));
            return result;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ECMWF 导入失败: " + ex.getMessage(), ex);
        } finally {
            if (output != null) {
                try {
                    Files.deleteIfExists(output);
                } catch (Exception ignored) {
                    // 临时文件删除失败不影响主流程
                }
            }
        }
    }

    private List<String> buildCommand(EcmwfImportRequest request, Path output) {
        List<String> command = new ArrayList<String>();
        command.add(resolvePythonExecutable());
        command.add(resolveScriptPath());
        command.add("--output");
        command.add(output.toAbsolutePath().toString());
        command.add("--time");
        command.add(String.valueOf(request.getTime() == null ? 0 : request.getTime()));
        command.add("--step");
        command.add(String.valueOf(request.getStep() == null ? 24 : request.getStep()));
        command.add("--param");
        command.add(request.getParam().trim());
        command.add("--type");
        command.add(defaultString(request.getType(), "fc"));
        command.add("--source");
        command.add(defaultString(request.getSource(), "ecmwf"));
        command.add("--model");
        command.add(defaultString(request.getModel(), "ifs"));

        if (!isBlank(request.getDate())) {
            command.add("--date");
            command.add(request.getDate().trim());
        }
        if (!isBlank(request.getLevtype())) {
            command.add("--levtype");
            command.add(request.getLevtype().trim());
        }
        if (request.getLevelist() != null) {
            command.add("--levelist");
            command.add(String.valueOf(request.getLevelist()));
        }
        if (!isBlank(request.getStream())) {
            command.add("--stream");
            command.add(request.getStream().trim());
        }
        return command;
    }

    private String resolvePythonExecutable() {
        if (!"python3".equalsIgnoreCase(pythonExecutable)) {
            return pythonExecutable;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "python";
        }
        return pythonExecutable;
    }

    private String resolveScriptPath() {
        Path direct = Paths.get(scriptPath);
        if (Files.exists(direct)) {
            return direct.toAbsolutePath().toString();
        }
        Path underDemo = Paths.get("demo_backend", scriptPath);
        if (Files.exists(underDemo)) {
            return underDemo.toAbsolutePath().toString();
        }
        Path fromUserDir = Paths.get(System.getProperty("user.dir", "."), scriptPath);
        if (Files.exists(fromUserDir)) {
            return fromUserDir.toAbsolutePath().toString();
        }
        return scriptPath;
    }

    private void validate(EcmwfImportRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }
        if (isBlank(request.getParam())) {
            throw badRequest("param 不能为空，例如 2t / msl / t / u / v");
        }
        int time = request.getTime() == null ? 0 : request.getTime();
        if (!VALID_TIMES.contains(time)) {
            throw badRequest("time 仅支持 0 / 6 / 12 / 18 UTC");
        }
        int step = request.getStep() == null ? 24 : request.getStep();
        if (step < 0 || step > 360) {
            throw badRequest("step 必须在 0-360 小时之间");
        }
        String source = defaultString(request.getSource(), "ecmwf");
        if (!VALID_SOURCES.contains(source)) {
            throw badRequest("source 仅支持 ecmwf / aws / google / azure");
        }
        String model = defaultString(request.getModel(), "ifs");
        if (!VALID_MODELS.contains(model)) {
            throw badRequest("model 仅支持 ifs / aifs-single / aifs-ens");
        }
        String type = defaultString(request.getType(), "fc");
        if (!VALID_TYPES.contains(type)) {
            throw badRequest("type 仅支持 fc / pf / em / es / ep");
        }
        forecastDataService.validateTarget(
                request.getDataset(), request.getYear(), request.getMonth(), request.getVarModel());
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
