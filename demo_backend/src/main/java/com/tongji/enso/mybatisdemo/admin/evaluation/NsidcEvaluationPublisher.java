package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NsidcEvaluationPublisher {
    private final EvaluationService evaluationService;
    private final AdminEvaluationMapper mapper;
    private final ObjectMapper objectMapper;

    public NsidcEvaluationPublisher(EvaluationService evaluationService, AdminEvaluationMapper mapper,
                                    ObjectMapper objectMapper) {
        this.evaluationService = evaluationService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> publish(EvaluationCategory category, JsonNode result) {
        JsonNode records = result.get("records");
        int inserted = 0;
        int updated = 0;
        List<Long> recordIds = new ArrayList<>();
        for (JsonNode node : records) {
            EvaluationRecordRequest request;
            try {
                request = objectMapper.treeToValue(node, EvaluationRecordRequest.class);
            } catch (Exception exception) {
                throw invalidOutput("NSIDC 指标记录无法解析");
            }
            EvaluationPersistenceRecord existing = evaluationService.findNaturalKey(category, request);
            if (existing == null) {
                evaluationService.create(category, request);
                inserted++;
            } else {
                evaluationService.update(category, existing.getId(), request);
                updated++;
            }
            EvaluationPersistenceRecord saved = evaluationService.findNaturalKey(category, request);
            if (saved == null || saved.getId() == null) throw invalidOutput("NSIDC 指标发布后无法读取");
            recordIds.add(saved.getId());
            upsertProvenance(category, saved.getId(), result);
        }
        Map<String, Object> publication = new LinkedHashMap<>();
        publication.put("mode", "UPSERT");
        publication.put("inserted", inserted);
        publication.put("updated", updated);
        publication.put("recordIds", recordIds);
        return publication;
    }

    private void upsertProvenance(EvaluationCategory category, long recordId, JsonNode result) {
        EvaluationMetricProvenance provenance = new EvaluationMetricProvenance();
        provenance.setCategory(category.name());
        provenance.setRecordId(recordId);
        provenance.setSource("NSIDC");
        provenance.setPredictionModel(result.path("predictionModel").asText());
        provenance.setObservationDataset(result.path("observation").path("datasetId").asText());
        provenance.setObservationVersion(result.path("observation").path("version").asText());
        provenance.setDetails(result.toString());
        Long id = mapper.findProvenanceId(category.name(), recordId);
        if (id == null) {
            mapper.insertProvenance(provenance);
        } else {
            provenance.setId(id);
            mapper.updateProvenance(provenance);
        }
    }

    private AdminException invalidOutput(String message) {
        return new AdminException("NSIDC_OUTPUT_INVALID", message, HttpStatus.BAD_GATEWAY);
    }
}
