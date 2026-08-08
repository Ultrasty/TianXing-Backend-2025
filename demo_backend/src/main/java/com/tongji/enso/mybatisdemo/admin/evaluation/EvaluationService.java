package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import com.tongji.enso.mybatisdemo.admin.common.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class EvaluationService {
    private final Map<EvaluationCategory, EvaluationDataAdapter> adapters = new EnumMap<>(EvaluationCategory.class);
    private final EvaluationValidator validator;
    private final ObjectMapper objectMapper;

    public EvaluationService(List<EvaluationDataAdapter> adapterList, EvaluationValidator validator,
                             ObjectMapper objectMapper) {
        for (EvaluationDataAdapter adapter : adapterList) {
            adapters.put(adapter.category(), adapter);
        }
        for (EvaluationCategory category : EvaluationCategory.values()) {
            if (!adapters.containsKey(category)) {
                throw new IllegalStateException("Missing evaluation adapter for " + category);
            }
        }
        this.validator = validator;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<EvaluationRecordResponse> list(EvaluationCategory category, EvaluationQueryRequest query) {
        validator.validateQuery(category, query);
        EvaluationDataAdapter adapter = adapter(category);
        int offset = (query.getPage() - 1) * query.getPageSize();
        List<EvaluationRecordResponse> responses = new ArrayList<>();
        for (EvaluationPersistenceRecord record : adapter.list(query, offset, query.getPageSize())) {
            responses.add(toResponse(category, record));
        }
        return new PageResult<>(query.getPage(), query.getPageSize(), adapter.count(query), responses);
    }

    @Transactional(readOnly = true)
    public EvaluationRecordResponse get(EvaluationCategory category, long id) {
        return toResponse(category, requireRecord(category, id));
    }

    @Transactional
    public EvaluationRecordResponse create(EvaluationCategory category, EvaluationRecordRequest request) {
        validator.validateAndNormalize(category, request);
        EvaluationDataAdapter adapter = adapter(category);
        if (adapter.findNaturalKey(request) != null) {
            duplicate();
        }
        EvaluationPersistenceRecord record = toPersistence(request);
        adapter.insert(record);
        return toResponse(category, requireRecord(category, record.getId()));
    }

    @Transactional
    public EvaluationRecordResponse update(EvaluationCategory category, long id, EvaluationRecordRequest request) {
        requireRecord(category, id);
        validator.validateAndNormalize(category, request);
        EvaluationDataAdapter adapter = adapter(category);
        EvaluationPersistenceRecord duplicate = adapter.findNaturalKey(request);
        if (duplicate != null && duplicate.getId() != null && duplicate.getId() != id) {
            duplicate();
        }
        EvaluationPersistenceRecord record = toPersistence(request);
        record.setId(id);
        if (adapter.update(record) != 1) {
            notFound();
        }
        return toResponse(category, requireRecord(category, id));
    }

    @Transactional
    public EvaluationRecordResponse delete(EvaluationCategory category, long id) {
        EvaluationPersistenceRecord existing = requireRecord(category, id);
        if (adapter(category).delete(id) != 1) {
            notFound();
        }
        return toResponse(category, existing);
    }

    @Transactional(readOnly = true)
    public EvaluationPersistenceRecord findNaturalKey(EvaluationCategory category, EvaluationRecordRequest request) {
        return adapter(category).findNaturalKey(request);
    }

    private EvaluationPersistenceRecord requireRecord(EvaluationCategory category, long id) {
        if (id <= 0) {
            notFound();
        }
        EvaluationPersistenceRecord record = adapter(category).get(id);
        if (record == null) {
            notFound();
        }
        return record;
    }

    private EvaluationDataAdapter adapter(EvaluationCategory category) {
        return adapters.get(category);
    }

    private EvaluationPersistenceRecord toPersistence(EvaluationRecordRequest request) {
        EvaluationPersistenceRecord record = new EvaluationPersistenceRecord();
        record.setYear(request.getYear());
        record.setMonth(request.getMonth());
        record.setDay(request.getDay());
        record.setVarModel(request.getVarModel());
        try {
            record.setData(objectMapper.writeValueAsString(request.getData()));
        } catch (JsonProcessingException exception) {
            throw new AdminException("EVALUATION_INVALID_DATA", "data 无法序列化为JSON", HttpStatus.BAD_REQUEST);
        }
        return record;
    }

    private EvaluationRecordResponse toResponse(EvaluationCategory category, EvaluationPersistenceRecord record) {
        try {
            JsonNode data = objectMapper.readTree(record.getData());
            return new EvaluationRecordResponse(category, record.getId(), record.getYear(), record.getMonth(),
                    record.getDay(), record.getVarModel(), data);
        } catch (JsonProcessingException exception) {
            throw new AdminException("DATABASE_OPERATION_FAILED", "数据库中的评估data不是有效JSON",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void duplicate() {
        throw new AdminException("EVALUATION_DUPLICATE", "评估数据自然键已存在", HttpStatus.CONFLICT);
    }

    private void notFound() {
        throw new AdminException("EVALUATION_NOT_FOUND", "评估数据不存在", HttpStatus.NOT_FOUND);
    }
}
