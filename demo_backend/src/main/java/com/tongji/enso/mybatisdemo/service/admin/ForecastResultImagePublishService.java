package com.tongji.enso.mybatisdemo.service.admin;

import com.tongji.enso.mybatisdemo.entity.online.Imgs;
import com.tongji.enso.mybatisdemo.mapper.online.ImgsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ForecastResultImagePublishService {
    private static final Set<String> SUPPORTED_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "SIC", "NAO", "ENSO_ASC", "ENSO_MC", "ENSO_GTC",
            "WEA_MSLP", "WEA_T2M", "WEA_TP", "WEA_U10"
    )));

    private static final Set<String> DAY_REQUIRED_TYPES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "SIC", "WEA_MSLP", "WEA_T2M", "WEA_TP", "WEA_U10"
    )));

    private static final Set<String> ALLOWED_EXTENSIONS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "png", "jpg", "jpeg", "webp", "gif"
    )));

    @Autowired
    private ImgsMapper imgsMapper;

    @Autowired
    private EcmwfRawDataService ecmwfRawDataService;

    @Value("${admin.upload.root:uploads}")
    private String uploadRoot;

    @Value("${admin.upload.public-prefix:/admin-files}")
    private String publicPrefix;

    public PublishedImage publishMultipart(String year, String month, String day, String type, MultipartFile[] files) {
        ImageKey key = normalizeKey(year, month, day, type);
        ensureNewRecord(key);
        List<String> paths = saveMultipartFiles(key, files);
        return persistRecord(key, paths);
    }

    public PublishedImage publishFromEcmwfUrls(String year, String month, String day, String type, List<String> imageUrls) {
        ImageKey key = normalizeKey(year, month, day, type);
        ensureNewRecord(key);
        List<String> paths = downloadEcmwfImages(key, imageUrls);
        return persistRecord(key, paths);
    }

    public PublishedImage publishFromEcmwfRaw(String year, String month, String day, String type,
                                              EcmwfRawDataService.GenerateRequest request) {
        ImageKey key = normalizeKey(year, month, day, type);
        ensureNewRecord(key);
        request.type = key.type;
        Path targetDirectory = targetDirectory(key);
        createDirectory(targetDirectory);
        Path rawDirectory = targetDirectory.resolve("_source").normalize();
        assertInsideUploadRoot(rawDirectory);
        EcmwfRawDataService.GeneratedForecast generated = ecmwfRawDataService.generate(request, rawDirectory);
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < generated.getImages().size(); i++) {
            Path image = generated.getImages().get(i);
            String extension = extensionFromName(image.getFileName().toString());
            String fileName = String.format(Locale.ENGLISH, "%02d.%s", i + 1,
                    isBlank(extension) ? "png" : extension);
            Path target = targetDirectory.resolve(fileName).normalize();
            assertInsideUploadRoot(target);
            try {
                Files.copy(image, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存 ECMWF 生成图片失败", ex);
            }
            paths.add(publicPath(key, fileName));
        }
        return persistRecord(key, paths);
    }

    public void deletePublishedImages(String year, String month, String day, String type) {
        ImageKey key = normalizeKey(year, month, day, type);
        int deleted = requiresDay(key.type)
                ? imgsMapper.deleteByYearMonthDayType(key.year, key.month, key.day, key.type)
                : imgsMapper.deleteByYearMonthType(key.year, key.month, key.type);
        if (deleted <= 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应的预报结果图记录");
        }
        deleteStoredFiles(key);
    }

    public DeletedSingleImage deletePublishedImage(String year, String month, String day, String type, String imagePath) {
        ImageKey key = normalizeKey(year, month, day, type);
        String normalizedImagePath = normalizeImagePath(imagePath);
        List<Imgs> records = requiresDay(key.type)
                ? imgsMapper.findImgsInfoByDayType(key.year, key.month, key.day, key.type)
                : findMonthRecords(key);
        if (records.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到对应的预报结果图记录");
        }

        for (Imgs record : records) {
            List<String> paths = splitPaths(record.getData());
            int index = findPathIndex(paths, normalizedImagePath);
            if (index < 0) {
                continue;
            }

            String deletedPath = paths.remove(index);
            if (paths.isEmpty()) {
                imgsMapper.deleteById(record.getId());
            } else {
                imgsMapper.updateDataById(record.getId(), String.join(",", paths));
            }
            deleteStoredFile(deletedPath);
            return new DeletedSingleImage(record.getId(), key.year, key.month, key.day, key.type, deletedPath, paths);
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到当前展示的预报结果图");
    }

    public List<ImageTypeOption> listSupportedTypes() {
        List<ImageTypeOption> options = new ArrayList<>();
        options.add(new ImageTypeOption("ENSO_ASC", "ENSO ASC", "月", false, "ENSO 模态预测结果图"));
        options.add(new ImageTypeOption("ENSO_MC", "ENSO MC", "月", false, "ENSO 模态预测结果图"));
        options.add(new ImageTypeOption("ENSO_GTC", "ENSO GTC", "月", false, "ENSO 模态预测结果图"));
        options.add(new ImageTypeOption("SIC", "海冰 SIC", "日", true, "海冰密集度预测结果图"));
        options.add(new ImageTypeOption("NAO", "NAO 格点图", "月", false, "NAO 格点预测结果图"));
        options.add(new ImageTypeOption("WEA_MSLP", "全球天气 MSLP", "日", true, "海平面气压预测结果图"));
        options.add(new ImageTypeOption("WEA_T2M", "全球天气 T2M", "日", true, "2 米气温预测结果图"));
        options.add(new ImageTypeOption("WEA_TP", "全球天气 TP", "日", true, "地表降水预测结果图"));
        options.add(new ImageTypeOption("WEA_U10", "全球天气 U10", "日", true, "10 米风预测结果图"));
        return options;
    }

    private void ensureNewRecord(ImageKey key) {
        int count;
        if (requiresDay(key.type)) {
            count = imgsMapper.countByYearMonthDayType(key.year, key.month, key.day, key.type);
        } else {
            count = imgsMapper.countByYearMonthType(key.year, key.month, key.type);
        }
        if (count > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该时间和类型的预报结果图已存在");
        }
    }

    private PublishedImage persistRecord(ImageKey key, List<String> paths) {
        int id = imgsMapper.findMaxId() + 1;
        Imgs imgs = new Imgs(id, key.year, key.month, dayForStorage(key), key.type, String.join(",", paths));
        imgsMapper.insertImgs(imgs);
        return new PublishedImage(imgs.getId(), key.year, key.month, key.day, key.type, paths, buildVerifyPath(key));
    }

    private String dayForStorage(ImageKey key) {
        return isBlank(key.day) ? "1" : key.day;
    }

    private List<Imgs> findMonthRecords(ImageKey key) {
        List<Imgs> all = imgsMapper.findImgsInfoByType(key.type);
        List<Imgs> matched = new ArrayList<>();
        for (Imgs record : all) {
            if (key.year.equals(record.getYear()) && key.month.equals(record.getMonth())) {
                matched.add(record);
            }
        }
        return matched;
    }

    private List<String> splitPaths(String data) {
        List<String> paths = new ArrayList<>();
        if (isBlank(data)) {
            return paths;
        }
        for (String value : data.split(",")) {
            if (!isBlank(value)) {
                paths.add(value.trim());
            }
        }
        return paths;
    }

    private int findPathIndex(List<String> paths, String imagePath) {
        for (int i = 0; i < paths.size(); i++) {
            if (normalizeImagePath(paths.get(i)).equals(imagePath)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeImagePath(String imagePath) {
        if (isBlank(imagePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择要删除的图片");
        }
        String normalized = imagePath.trim();
        try {
            URL url = new URL(normalized);
            normalized = url.getPath();
        } catch (Exception ignored) {
        }
        int queryIndex = normalized.indexOf('?');
        if (queryIndex >= 0) {
            normalized = normalized.substring(0, queryIndex);
        }
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private String buildVerifyPath(ImageKey key) {
        if (key.type.startsWith("ENSO_")) {
            return "/imgs/predictionResult/ssta?year=" + key.year + "&month=" + key.month;
        }
        if ("SIC".equals(key.type)) {
            return "/seaice/predictionResult/SIC?year=" + key.year + "&month=" + key.month + "&day=" + key.day;
        }
        if ("NAO".equals(key.type)) {
            return "/nao/findGridData/nao?year=" + key.year + "&month=" + key.month;
        }
        if (key.type.startsWith("WEA_")) {
            return "/imgs/" + key.type + "/getImgsPath?year=" + key.year + "&month=" + key.month + "&day=" + key.day;
        }
        return null;
    }

    private List<String> saveMultipartFiles(ImageKey key, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请至少上传一张图片");
        }
        Path targetDirectory = targetDirectory(key);
        createDirectory(targetDirectory);

        List<String> paths = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传图片不能为空");
            }
            String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
            String fileName = String.format(Locale.ENGLISH, "%02d.%s", i + 1, extension);
            Path target = targetDirectory.resolve(fileName).normalize();
            assertInsideUploadRoot(target);
            try {
                file.transferTo(target.toFile());
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存上传图片失败", ex);
            }
            paths.add(publicPath(key, fileName));
        }
        return paths;
    }

    private List<String> downloadEcmwfImages(ImageKey key, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供 ECMWF 图片地址");
        }
        Path targetDirectory = targetDirectory(key);
        createDirectory(targetDirectory);

        List<String> paths = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            String value = imageUrls.get(i);
            if (isBlank(value)) {
                continue;
            }
            URL url = parseEcmwfUrl(value.trim());
            URLConnection connection;
            try {
                connection = url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法连接 ECMWF 图片地址", ex);
            }

            String extension = resolveExtension(url.getPath(), connection.getContentType());
            String fileName = String.format(Locale.ENGLISH, "%02d.%s", paths.size() + 1, extension);
            Path target = targetDirectory.resolve(fileName).normalize();
            assertInsideUploadRoot(target);
            try (InputStream inputStream = connection.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "下载 ECMWF 图片失败", ex);
            }
            paths.add(publicPath(key, fileName));
        }

        if (paths.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供有效的 ECMWF 图片地址");
        }
        return paths;
    }

    private ImageKey normalizeKey(String year, String month, String day, String type) {
        String normalizedType = normalizeType(type);
        String normalizedYear = normalizeNumber(year, "year", 1900, 2200);
        String normalizedMonth = normalizeNumber(month, "month", 1, 12);
        String normalizedDay = null;
        if (requiresDay(normalizedType)) {
            normalizedDay = normalizeNumber(day, "day", 1, 31);
        } else if (!isBlank(day)) {
            normalizedDay = normalizeNumber(day, "day", 1, 31);
        }
        return new ImageKey(normalizedYear, normalizedMonth, normalizedDay, normalizedType);
    }

    private String normalizeType(String type) {
        if (isBlank(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择图片类型");
        }
        String normalized = type.trim().toUpperCase(Locale.ENGLISH);
        if (!SUPPORTED_TYPES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的图片类型：" + type);
        }
        return normalized;
    }

    private String normalizeNumber(String value, String fieldName, int min, int max) {
        if (isBlank(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 必须是数字");
        }
        if (parsed < min || parsed > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " 超出允许范围");
        }
        return String.valueOf(parsed);
    }

    private boolean requiresDay(String type) {
        return DAY_REQUIRED_TYPES.contains(type);
    }

    private URL parseEcmwfUrl(String value) {
        try {
            URL url = new URL(value);
            String protocol = url.getProtocol().toLowerCase(Locale.ENGLISH);
            String host = url.getHost().toLowerCase(Locale.ENGLISH);
            if (!("https".equals(protocol) || "http".equals(protocol))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ECMWF 地址仅支持 HTTP/HTTPS");
            }
            if (!(host.contains("ecmwf") || host.contains("copernicus"))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入 ECMWF 或 Copernicus 来源的图片地址");
            }
            return url;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ECMWF 图片地址格式不正确", ex);
        }
    }

    private String resolveExtension(String fileNameOrPath, String contentType) {
        String extension = extensionFromName(fileNameOrPath);
        if (isBlank(extension)) {
            extension = extensionFromContentType(contentType);
        }
        if (isBlank(extension) || !ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 png、jpg、jpeg、webp、gif 图片");
        }
        if (!isBlank(contentType) && !contentType.toLowerCase(Locale.ENGLISH).startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文件类型必须是图片");
        }
        return extension;
    }

    private String extensionFromName(String fileNameOrPath) {
        if (isBlank(fileNameOrPath)) {
            return null;
        }
        String cleanName = fileNameOrPath;
        int queryIndex = cleanName.indexOf('?');
        if (queryIndex >= 0) {
            cleanName = cleanName.substring(0, queryIndex);
        }
        int slashIndex = Math.max(cleanName.lastIndexOf('/'), cleanName.lastIndexOf('\\'));
        if (slashIndex >= 0) {
            cleanName = cleanName.substring(slashIndex + 1);
        }
        int dotIndex = cleanName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == cleanName.length() - 1) {
            return null;
        }
        return cleanName.substring(dotIndex + 1).toLowerCase(Locale.ENGLISH);
    }

    private String extensionFromContentType(String contentType) {
        if (isBlank(contentType)) {
            return null;
        }
        String normalized = contentType.toLowerCase(Locale.ENGLISH);
        int semicolonIndex = normalized.indexOf(';');
        if (semicolonIndex >= 0) {
            normalized = normalized.substring(0, semicolonIndex);
        }
        if ("image/png".equals(normalized)) {
            return "png";
        }
        if ("image/jpeg".equals(normalized)) {
            return "jpg";
        }
        if ("image/webp".equals(normalized)) {
            return "webp";
        }
        if ("image/gif".equals(normalized)) {
            return "gif";
        }
        return null;
    }

    private Path targetDirectory(ImageKey key) {
        Path root = uploadRootPath();
        Path directory = root.resolve("forecast-result-images")
                .resolve(key.type)
                .resolve(key.year)
                .resolve(key.month);
        if (requiresDay(key.type)) {
            directory = directory.resolve(key.day);
        }
        Path normalized = directory.normalize();
        if (!normalized.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传路径非法");
        }
        return normalized;
    }

    private void deleteStoredFiles(ImageKey key) {
        Path directory = targetDirectory(key);
        if (!Files.exists(directory)) {
            return;
        }
        try {
            Files.walk(directory)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof IOException) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除图片文件失败", cause);
            }
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除图片文件失败", ex);
        }
    }

    private void deleteStoredFile(String publicImagePath) {
        Path root = uploadRootPath();
        String normalizedPrefix = normalizePublicPrefix(publicPrefix);
        String normalizedPath = normalizeImagePath(publicImagePath);
        if (!normalizedPath.startsWith(normalizedPrefix + "/")) {
            return;
        }
        String relative = normalizedPath.substring(normalizedPrefix.length() + 1);
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "图片路径非法");
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "删除图片文件失败", ex);
        }
    }

    private String publicPath(ImageKey key, String fileName) {
        StringBuilder builder = new StringBuilder();
        builder.append(normalizePublicPrefix(publicPrefix))
                .append("/forecast-result-images/")
                .append(key.type)
                .append("/")
                .append(key.year)
                .append("/")
                .append(key.month);
        if (requiresDay(key.type)) {
            builder.append("/").append(key.day);
        }
        builder.append("/").append(fileName);
        return builder.toString();
    }

    private Path uploadRootPath() {
        return Paths.get(uploadRoot).toAbsolutePath().normalize();
    }

    private void createDirectory(Path targetDirectory) {
        try {
            Files.createDirectories(targetDirectory);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "创建图片目录失败", ex);
        }
    }

    private void assertInsideUploadRoot(Path target) {
        if (!target.normalize().startsWith(uploadRootPath())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传路径非法");
        }
    }

    private String normalizePublicPrefix(String prefix) {
        if (isBlank(prefix)) {
            return "/admin-files";
        }
        String normalized = prefix.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class ImageKey {
        private final String year;
        private final String month;
        private final String day;
        private final String type;

        private ImageKey(String year, String month, String day, String type) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.type = type;
        }
    }

    public static class DeletedSingleImage {
        private final int id;
        private final String year;
        private final String month;
        private final String day;
        private final String type;
        private final String deletedPath;
        private final List<String> remainingPaths;

        private DeletedSingleImage(int id, String year, String month, String day, String type,
                                   String deletedPath, List<String> remainingPaths) {
            this.id = id;
            this.year = year;
            this.month = month;
            this.day = day;
            this.type = type;
            this.deletedPath = deletedPath;
            this.remainingPaths = remainingPaths;
        }

        public int getId() {
            return id;
        }

        public String getYear() {
            return year;
        }

        public String getMonth() {
            return month;
        }

        public String getDay() {
            return day;
        }

        public String getType() {
            return type;
        }

        public String getDeletedPath() {
            return deletedPath;
        }

        public List<String> getRemainingPaths() {
            return remainingPaths;
        }
    }

    public static class PublishedImage {
        private final int id;
        private final String year;
        private final String month;
        private final String day;
        private final String type;
        private final List<String> paths;
        private final String verifyPath;

        private PublishedImage(int id, String year, String month, String day, String type, List<String> paths, String verifyPath) {
            this.id = id;
            this.year = year;
            this.month = month;
            this.day = day;
            this.type = type;
            this.paths = paths;
            this.verifyPath = verifyPath;
        }

        public int getId() {
            return id;
        }

        public String getYear() {
            return year;
        }

        public String getMonth() {
            return month;
        }

        public String getDay() {
            return day;
        }

        public String getType() {
            return type;
        }

        public List<String> getPaths() {
            return paths;
        }

        public String getVerifyPath() {
            return verifyPath;
        }
    }

    public static class ImageTypeOption {
        private final String value;
        private final String label;
        private final String period;
        private final boolean requiresDay;
        private final String description;

        private ImageTypeOption(String value, String label, String period, boolean requiresDay, String description) {
            this.value = value;
            this.label = label;
            this.period = period;
            this.requiresDay = requiresDay;
            this.description = description;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }

        public String getPeriod() {
            return period;
        }

        public boolean isRequiresDay() {
            return requiresDay;
        }

        public String getDescription() {
            return description;
        }
    }
}
