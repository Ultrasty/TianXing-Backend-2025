package com.tongji.enso.mybatisdemo.service.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class EcmwfRawDataService {
    private static final DateTimeFormatter ECMWF_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Value("${ecmwf.open-data-root:https://data.ecmwf.int/forecasts}")
    private String openDataRoot;

    @Value("${ecmwf.api-key:}")
    private String apiKey;

    @Value("${ecmwf.api-email:}")
    private String apiEmail;

    @Value("${ecmwf.download-timeout-ms:120000}")
    private int timeoutMs;

    @Value("${ecmwf.plotter-command:python}")
    private String plotterCommand;

    @Value("${ecmwf.plotter-script:scripts/ecmwf_plot.py}")
    private String plotterScript;

    public GeneratedForecast generate(GenerateRequest request, Path outputDirectory) {
        validate(request);
        try {
            Files.createDirectories(outputDirectory);
            String extension = request.format.toLowerCase(Locale.ENGLISH).contains("netcdf") ? "nc" : "grib2";
            Path rawFile = outputDirectory.resolve("source." + extension);
            String sourceUrl = isBlank(request.dataUrl) ? buildOpenDataUrl(request) : request.dataUrl.trim();
            download(sourceUrl, rawFile);

            Path outputPrefix = outputDirectory.resolve("forecast");
            List<String> command = new ArrayList<>();
            command.add(plotterCommand);
            command.add(plotterScript);
            command.add("--input");
            command.add(rawFile.toString());
            command.add("--output");
            command.add(outputPrefix.toString());
            command.add("--variable");
            command.add(request.variable);
            command.add("--title");
            command.add(request.title == null ? request.type : request.title);
            command.add("--format");
            command.add(request.format);
            runPlotter(command);

            List<Path> images = new ArrayList<>();
            try (java.util.stream.Stream<Path> files = Files.list(outputDirectory)) {
                files.filter(path -> path.getFileName().toString().startsWith("forecast-")
                                && path.getFileName().toString().endsWith(".png"))
                        .sorted()
                        .forEach(images::add);
            }
            if (images.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ECMWF 数据已下载，但未生成图片");
            }
            return new GeneratedForecast(sourceUrl, rawFile, images);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "ECMWF 原始数据处理失败", ex);
        }
    }

    private String buildOpenDataUrl(GenerateRequest request) {
        LocalDate date = LocalDate.parse(request.forecastDate, DateTimeFormatter.ISO_LOCAL_DATE);
        String ymd = date.format(ECMWF_DATE);
        String hh = String.format(Locale.ENGLISH, "%02d", request.runHour);
        return trimRight(openDataRoot) + "/" + ymd + "/" + hh + "z/ifs/0p25/oper/"
                + ymd + hh + "0000-" + request.step + "h-oper-fc.grib2";
    }

    private void download(String sourceUrl, Path target) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(sourceUrl).openConnection();
        connection.setConnectTimeout(timeoutMs);
        connection.setReadTimeout(timeoutMs);
        connection.setRequestProperty("Accept", "application/octet-stream");
        if (!isBlank(apiKey)) {
            String credentials = (isBlank(apiEmail) ? "" : apiEmail) + ":" + apiKey;
            connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        }
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "ECMWF 下载接口返回 HTTP " + status + "：" + readError(connection));
        }
        try (java.io.InputStream input = connection.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
        }
    }

    private void runPlotter(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "绘图任务失败：" + output);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "绘图任务被中断", ex);
        }
    }

    private String readError(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
            String line = reader.readLine();
            return line == null ? "无错误详情" : line;
        } catch (Exception ex) {
            return "无错误详情";
        }
    }

    private void validate(GenerateRequest request) {
        if (request == null || isBlank(request.forecastDate) || request.runHour < 0 || request.runHour > 23
                || request.step < 0 || request.step > 360 || isBlank(request.variable)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供有效的 ECMWF 日期、起报时次、步长和变量");
        }
        try {
            LocalDate.parse(request.forecastDate, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "forecastDate 必须是 YYYY-MM-DD");
        }
        if (!"GRIB".equalsIgnoreCase(request.format) && !"NETCDF".equalsIgnoreCase(request.format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "format 仅支持 GRIB 或 NETCDF");
        }
    }

    private String trimRight(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class GenerateRequest {
        public String forecastDate;
        public int runHour = 0;
        public int step = 24;
        public String variable = "2t";
        public String format = "GRIB";
        public String dataUrl;
        public String title;
        public String type;
    }

    public static class GeneratedForecast {
        private final String sourceUrl;
        private final Path rawFile;
        private final List<Path> images;

        public GeneratedForecast(String sourceUrl, Path rawFile, List<Path> images) {
            this.sourceUrl = sourceUrl;
            this.rawFile = rawFile;
            this.images = images;
        }

        public String getSourceUrl() { return sourceUrl; }
        public Path getRawFile() { return rawFile; }
        public List<Path> getImages() { return images; }
    }
}
