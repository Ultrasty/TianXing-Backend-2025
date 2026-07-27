package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NaoEvaluationAdapter implements EvaluationDataAdapter {
    private final AdminEvaluationMapper mapper;

    public NaoEvaluationAdapter(AdminEvaluationMapper mapper) { this.mapper = mapper; }
    @Override public EvaluationCategory category() { return EvaluationCategory.NAO; }
    @Override public List<EvaluationPersistenceRecord> list(EvaluationQueryRequest query, int offset, int limit) {
        return mapper.listNao(query, offset, limit);
    }
    @Override public long count(EvaluationQueryRequest query) { return mapper.countNao(query); }
    @Override public EvaluationPersistenceRecord get(long id) { return mapper.getNao(id); }
    @Override public EvaluationPersistenceRecord findNaturalKey(EvaluationRecordRequest request) {
        return mapper.findNaoKey(request.getYear(), request.getMonth(), request.getVarModel());
    }
    @Override public void insert(EvaluationPersistenceRecord record) { mapper.insertNao(record); }
    @Override public int update(EvaluationPersistenceRecord record) { return mapper.updateNao(record); }
    @Override public int delete(long id) { return mapper.deleteNao(id); }
}
