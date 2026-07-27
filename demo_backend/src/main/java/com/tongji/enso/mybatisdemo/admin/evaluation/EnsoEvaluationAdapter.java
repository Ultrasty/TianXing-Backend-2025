package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EnsoEvaluationAdapter implements EvaluationDataAdapter {
    private final AdminEvaluationMapper mapper;

    public EnsoEvaluationAdapter(AdminEvaluationMapper mapper) {
        this.mapper = mapper;
    }

    @Override public EvaluationCategory category() { return EvaluationCategory.ENSO; }
    @Override public List<EvaluationPersistenceRecord> list(EvaluationQueryRequest query, int offset, int limit) {
        return mapper.listEnso(query, offset, limit);
    }
    @Override public long count(EvaluationQueryRequest query) { return mapper.countEnso(query); }
    @Override public EvaluationPersistenceRecord get(long id) { return mapper.getEnso(id); }
    @Override public EvaluationPersistenceRecord findNaturalKey(EvaluationRecordRequest request) {
        return mapper.findEnsoKey(request.getYear());
    }
    @Override public void insert(EvaluationPersistenceRecord record) { mapper.insertEnso(record); }
    @Override public int update(EvaluationPersistenceRecord record) { return mapper.updateEnso(record); }
    @Override public int delete(long id) { return mapper.deleteEnso(id); }
}
