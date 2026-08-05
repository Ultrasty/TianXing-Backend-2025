package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class EvaluationImportService {
    private static final Set<String> ALLOWED_SOURCES = new HashSet<>();

    static {
        ALLOWED_SOURCES.add("MANUAL");
        ALLOWED_SOURCES.add("ECMWF");
    }

    private final ObjectMapper objectMapper;
    private final EvaluationValidator validator;
    private final EvaluationService evaluationService;
    private final int maxRecords;
    private final long maxFileSizeBytes;

    public EvaluationImportService(ObjectMapper objectMapper, EvaluationValidator validator,
                                   EvaluationService evaluationService,
                                   @Value("${admin.import.max-records:500}") int maxRecords,
                                   @Value("${admin.import.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        if (maxRecords <= 0 || maxFileSizeBytes <= 0) {
            throw new IllegalStateException("Admin import limits must be positive");
        }
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.evaluationService = evaluationService;
        this.maxRecords = maxRecords;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public EvaluationBatchImportResult importManual(MultipartFile file, String categoryValue, String modeValue) {
        if (file == null || file.isEmpty()) {
            throw new AdminException("IMPORT_FILE_EMPTY", "上传文件不能为空", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new AdminException("IMPORT_FILE_TOO_LARGE", "上传文件超过大小限制", HttpStatus.PAYLOAD_TOO_LARGE);
        }
        EvaluationBatchImportRequest request;
        try {
            request = objectMapper.readValue(file.getInputStream(), EvaluationBatchImportRequest.class);
        } catch (JsonProcessingException exception) {
            throw new AdminException("IMPORT_FILE_INVALID", "上传文件不是有效的JSON导入文件", HttpStatus.BAD_REQUEST);
        } catch (IOException exception) {
            throw new AdminException("IMPORT_FILE_INVALID", "无法读取上传文件", HttpStatus.BAD_REQUEST);
        }

        EvaluationCategory parameterCategory = EvaluationCategory.parse(categoryValue);
        if (request.getCategory() != null && EvaluationCategory.parse(request.getCategory()) != parameterCategory) {
            throw new AdminException("IMPORT_FILE_INVALID", "文件中的category与请求参数不一致", HttpStatus.BAD_REQUEST);
        }
        request.setCategory(parameterCategory.name());
        request.setMode(modeValue);
        request.setSource("MANUAL");
        return doImport(request);
    }

    @Transactional
    public EvaluationBatchImportResult importBatch(EvaluationBatchImportRequest request) {
        if (request == null || request.getSource() == null ||
                !"ECMWF".equals(request.getSource().trim().toUpperCase(Locale.ROOT))) {
            invalidFile("规范化批量接口的source必须是 ECMWF");
        }
        if (request.getDataKind() == null ||
                !"EVALUATION_METRIC".equals(request.getDataKind().trim().toUpperCase(Locale.ROOT))) {
            invalidFile("规范化批量接口的dataKind必须是 EVALUATION_METRIC；ECMWF原始场归约结果不能直接入评估表");
        }
        return doImport(request);
    }

    private EvaluationBatchImportResult doImport(EvaluationBatchImportRequest request) {
        if (request == null) {
            invalidFile("批量导入请求不能为空");
        }
        EvaluationCategory category = EvaluationCategory.parse(request.getCategory());
        ImportMode mode = ImportMode.parse(request.getMode());
        String source = normalizeSource(request.getSource());
        List<EvaluationRecordRequest> records = request.getRecords();
        if (records == null || records.isEmpty()) {
            throw new AdminException("IMPORT_FILE_EMPTY", "records不能为空", HttpStatus.BAD_REQUEST);
        }
        if (records.size() > maxRecords) {
            throw new AdminException("IMPORT_TOO_MANY_RECORDS", "导入记录数超过限制", HttpStatus.PAYLOAD_TOO_LARGE);
        }

        Map<String, Integer> keys = new HashMap<>();
        for (int index = 0; index < records.size(); index++) {
            EvaluationRecordRequest record = records.get(index);
            if (record != null && record.getSource() != null &&
                    !source.equals(record.getSource().trim().toUpperCase(Locale.ROOT))) {
                invalidFile("记录source与批次source不一致");
            }
            if (record != null) {
                record.setSource(source);
            }
            validator.validateAndNormalize(category, record);
            String key = validator.naturalKey(category, record);
            Integer previous = keys.put(key, index);
            if (previous != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("firstIndex", previous);
                details.put("duplicateIndex", index);
                throw new AdminException("EVALUATION_DUPLICATE", "导入文件内存在重复自然键",
                        HttpStatus.CONFLICT, details);
            }
        }

        if (mode == ImportMode.REJECT) {
            for (int index = 0; index < records.size(); index++) {
                if (evaluationService.findNaturalKey(category, records.get(index)) != null) {
                    Map<String, Object> details = new HashMap<>();
                    details.put("recordIndex", index);
                    throw new AdminException("EVALUATION_DUPLICATE", "数据库已存在相同自然键，整批未写入",
                            HttpStatus.CONFLICT, details);
                }
            }
        }

        int inserted = 0;
        int updated = 0;
        for (EvaluationRecordRequest record : records) {
            EvaluationPersistenceRecord existing = evaluationService.findNaturalKey(category, record);
            if (existing == null) {
                evaluationService.create(category, record);
                inserted++;
            } else if (mode == ImportMode.UPSERT) {
                evaluationService.update(category, existing.getId(), record);
                updated++;
            } else {
                // REJECT duplicates were checked before any write; this only covers a concurrent insert.
                throw new AdminException("EVALUATION_DUPLICATE", "导入过程中检测到并发重复数据",
                        HttpStatus.CONFLICT);
            }
        }
        return new EvaluationBatchImportResult(category, source, mode.name(), records.size(), inserted, updated);
    }

    private String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) {
            invalidFile("source不能为空");
        }
        String normalized = source.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SOURCES.contains(normalized)) {
            invalidFile("source必须是 MANUAL 或 ECMWF");
        }
        return normalized;
    }

    private void invalidFile(String message) {
        throw new AdminException("IMPORT_FILE_INVALID", message, HttpStatus.BAD_REQUEST);
    }
}
