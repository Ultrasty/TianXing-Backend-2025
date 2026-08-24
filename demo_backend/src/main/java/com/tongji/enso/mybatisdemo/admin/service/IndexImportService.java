package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.dto.IndexImportRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndexImportService {

    @Autowired
    private ForecastDataService forecastDataService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${admin.ecmwf.python:python3}")
    private String pythonExecutable;

    public Map<String, Object> importIndexFromNoaa(IndexImportRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }

        forecastDataService.validateTarget(
                request.getDataset(),
                request.getYear(),
                request.getMonth(),
                request.getVarModel()
        );

        File scriptFile = new File("scripts/index_fetch.py");
        if (!scriptFile.exists()) {
            scriptFile = new File("demo_backend/scripts/index_fetch.py");
        }
        if (!scriptFile.exists()) {
            scriptFile = new File(System.getProperty("user.dir"), "scripts/index_fetch.py");
        }
        if (!scriptFile.exists()) {
            scriptFile = new File(System.getProperty("user.dir"), "demo_backend/scripts/index_fetch.py");
        }
        if (!scriptFile.exists()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 NOAA 抓取脚本: " + scriptFile.getAbsolutePath());
        }

        if ("ENSO".equalsIgnoreCase(request.getDataset()) && !"nino34_asc".equalsIgnoreCase(request.getVarModel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ENSO数据集在线拉取只支持 nino34_asc 模型，其余特定 AI 模型须使用【手动上传】");
        }
        if ("NAO".equalsIgnoreCase(request.getDataset()) && request.getVarModel() != null && !request.getVarModel().toLowerCase().contains("nao")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NAO数据集只允许拉取 NAO 相关模型数据");
        }

        List<String> command = new ArrayList<String>();
        command.add(pythonExecutable);
        command.add(scriptFile.getAbsolutePath());
        command.add("--dataset");
        command.add(request.getDataset());
        command.add("--year");
        command.add(request.getYear());
        command.add("--month");
        command.add(request.getMonth());
        command.add("--var_model");
        command.add(request.getVarModel());

        if (request.getSource() != null && !request.getSource().trim().isEmpty()) {
            command.add("--source");
            command.add(request.getSource().trim());
        }
        if (request.getLeadMonths() != null && request.getLeadMonths() > 0) {
            command.add("--lead_months");
            command.add(String.valueOf(request.getLeadMonths()));
        }
        if (request.getSmoothing() != null && !request.getSmoothing().trim().isEmpty()) {
            command.add("--smoothing");
            command.add(request.getSmoothing().trim());
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = null;
        try {
            process = pb.start();
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();

            String output = readFully(stdout);
            String errOutput = readFully(stderr);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "NOAA Index 抓取失败 (exit code " + exitCode + "): " + errOutput
                );
            }

            JsonNode root = objectMapper.readTree(output);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray() || dataNode.size() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOAA 抓取脚本未返回合法的 1D Index 数组");
            }

            validateIndexValues(dataNode, request.getDataset());

            Map<String, Object> inserted = forecastDataService.createFromDecodedJson(
                    request.getDataset(),
                    request.getYear(),
                    request.getMonth(),
                    request.getVarModel(),
                    objectMapper.writeValueAsString(dataNode),
                    request.getOverwrite()
            );

            Map<String, Object> result = new LinkedHashMap<String, Object>();
            result.put("record", inserted);
            result.put("noaa", objectMapper.convertValue(root.get("metadata"), Map.class));
            return result;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "执行 NOAA 抓取时发生异常: " + ex.getMessage());
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static String readFully(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int read;
        while ((read = inputStream.read(data)) != -1) {
            buffer.write(data, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    static final double MAX_ABS_INDEX_VALUE = 5.0;

    static void validateIndexValues(JsonNode dataNode, String dataset) {
        for (JsonNode item : dataNode) {
            if (!item.isNumber()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "NOAA 返回的指数序列包含非数值元素，已拒绝入库");
            }
            double v = item.doubleValue();
            if (!Double.isFinite(v) || Math.abs(v) > MAX_ABS_INDEX_VALUE) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "NOAA 返回的 " + dataset + " 指数值 " + v + " 超出合理范围(±"
                                + MAX_ABS_INDEX_VALUE + ")，疑似数据源异常，已拒绝入库");
            }
        }
    }
}
