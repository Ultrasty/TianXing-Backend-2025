package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SieEvaluationAdapter implements EvaluationDataAdapter {
    private final AdminEvaluationMapper mapper;

    public SieEvaluationAdapter(AdminEvaluationMapper mapper) { this.mapper = mapper; }
    @Override public EvaluationCategory category() { return EvaluationCategory.SIE; }
    @Override public List<EvaluationPersistenceRecord> list(EvaluationQueryRequest query, int offset, int limit) {
        return mapper.listSie(query, offset, limit);
    }
    @Override public long count(EvaluationQueryRequest query) { return mapper.countSie(query); }
    @Override public EvaluationPersistenceRecord get(long id) { return mapper.getSie(id); }
    @Override public EvaluationPersistenceRecord findNaturalKey(EvaluationRecordRequest request) {
        return mapper.findSieKey(request.getYear(), request.getMonth(), request.getVarModel());
    }
    @Override public void insert(EvaluationPersistenceRecord record) { mapper.insertSie(record); }
    @Override public int update(EvaluationPersistenceRecord record) { return mapper.updateSie(record); }
    @Override public int delete(long id) { return mapper.deleteSie(id); }
}
