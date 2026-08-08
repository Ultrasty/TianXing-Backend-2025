package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SicEvaluationAdapter implements EvaluationDataAdapter {
    private final AdminEvaluationMapper mapper;

    public SicEvaluationAdapter(AdminEvaluationMapper mapper) { this.mapper = mapper; }
    @Override public EvaluationCategory category() { return EvaluationCategory.SIC; }
    @Override public List<EvaluationPersistenceRecord> list(EvaluationQueryRequest query, int offset, int limit) {
        return mapper.listSic(query, offset, limit);
    }
    @Override public long count(EvaluationQueryRequest query) { return mapper.countSic(query); }
    @Override public EvaluationPersistenceRecord get(long id) { return mapper.getSic(id); }
    @Override public EvaluationPersistenceRecord findNaturalKey(EvaluationRecordRequest request) {
        return mapper.findSicKey(request.getYear(), request.getMonth(), request.getDay(), request.getVarModel());
    }
    @Override public void insert(EvaluationPersistenceRecord record) { mapper.insertSic(record); }
    @Override public int update(EvaluationPersistenceRecord record) { return mapper.updateSic(record); }
    @Override public int delete(long id) { return mapper.deleteSic(id); }
}
