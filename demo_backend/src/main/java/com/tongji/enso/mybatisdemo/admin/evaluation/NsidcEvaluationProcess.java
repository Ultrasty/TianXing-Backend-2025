package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class NsidcEvaluationProcess {
    private final ObjectMapper objectMapper;

    @Value("${admin.nsidc.python:python}")
    private String pythonExecutable;
    @Value("${admin.nsidc.script:scripts/nsidc_evaluation.py}")
    private String scriptPath;
    @Value("${admin.nsidc.cache-dir:${java.io.tmpdir}/tianxing-nsidc-cache}")
    private String cacheDirectory;
    @Value("${admin.nsidc.timeout-seconds:900}")
    private long timeoutSeconds;
    @Value("${admin.nsidc.max-output-bytes:5242880}")
    private long maxOutputBytes;
    @Value("${admin.nsidc.download-workers:12}")
    private int downloadWorkers;

    public NsidcEvaluationProcess(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode execute(JsonNode input) {
        Path inputFile = null;
        Path outputFile = null;
        Path logFile = null;
        try {
            inputFile = Files.createTempFile("tianxing-nsidc-input-", ".json");
            outputFile = Files.createTempFile("tianxing-nsidc-output-", ".json");
            logFile = Files.createTempFile("tianxing-nsidc-", ".log");
            objectMapper.writeValue(inputFile.toFile(), input);
            ProcessBuilder builder = new ProcessBuilder(command(inputFile, outputFile));
            builder.redirectErrorStream(true);
            builder.redirectOutput(logFile.toFile());
            Process process = builder.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new AdminException("NSIDC_EVALUATION_TIMEOUT", "NSIDC 下载或指标计算超时",
                        HttpStatus.GATEWAY_TIMEOUT);
            }
            String log = new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new AdminException("NSIDC_EVALUATION_FAILED", "NSIDC 指标计算失败: " + truncate(log, 1200),
                        HttpStatus.BAD_GATEWAY);
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0 || Files.size(outputFile) > maxOutputBytes) {
                throw new AdminException("NSIDC_OUTPUT_INVALID", "NSIDC 指标结果为空或超过大小限制",
                        HttpStatus.BAD_GATEWAY);
            }
            try (InputStream stream = Files.newInputStream(outputFile)) {
                return objectMapper.readTree(stream);
            }
        } catch (AdminException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AdminException("NSIDC_EVALUATION_FAILED", "NSIDC 指标计算被中断", HttpStatus.BAD_GATEWAY);
        } catch (Exception exception) {
            throw new AdminException("NSIDC_EVALUATION_FAILED", "NSIDC 指标计算失败: " + exception.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        } finally {
            deleteQuietly(inputFile);
            deleteQuietly(outputFile);
            deleteQuietly(logFile);
        }
    }

    private List<String> command(Path input, Path output) {
        List<String> command = new ArrayList<>();
        command.add(pythonExecutable);
        command.add(resolveScriptPath(scriptPath));
        command.add("--input");
        command.add(input.toAbsolutePath().toString());
        command.add("--output");
        command.add(output.toAbsolutePath().toString());
        command.add("--cache-dir");
        command.add(Paths.get(cacheDirectory).toAbsolutePath().toString());
        command.add("--download-workers");
        command.add(String.valueOf(downloadWorkers));
        return command;
    }

    private String resolveScriptPath(String configuredPath) {
        Path direct = Paths.get(configuredPath);
        if (Files.exists(direct)) return direct.toAbsolutePath().toString();
        Path fromRepositoryRoot = Paths.get("demo_backend", configuredPath);
        if (Files.exists(fromRepositoryRoot)) return fromRepositoryRoot.toAbsolutePath().toString();
        return direct.toAbsolutePath().toString();
    }

    private String truncate(String value, int maximum) {
        if (value == null || value.length() <= maximum) return value;
        return value.substring(0, maximum) + "...";
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // Temporary request/result files are best-effort cleanup only.
        }
    }
}
