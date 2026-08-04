package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.dto.IndexImportRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
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
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "缺少 NOAA 抓取脚本: " + scriptFile.getAbsolutePath());
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

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = null;
        try {
            process = pb.start();
            InputStream stdout = process.getInputStream();
            InputStream stderr = process.getErrorStream();

            String output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
            String errOutput = new String(stderr.readAllBytes(), StandardCharsets.UTF_8);

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "NOAA Index 抓取失败 (exit code " + exitCode + "): " + errOutput
                );
            }

            JsonNode root = objectMapper.readTree(output);
            JsonNode dataNode = root.get("data");
            if (dataNode == null || !dataNode.isArray()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "NOAA 抓取脚本未返回合法的 1D Index 数组");
            }

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
}
