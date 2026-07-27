package com.tongji.enso.mybatisdemo.admin.evaluation;

import java.util.List;

public interface EvaluationDataAdapter {
    EvaluationCategory category();

    List<EvaluationPersistenceRecord> list(EvaluationQueryRequest query, int offset, int limit);

    long count(EvaluationQueryRequest query);

    EvaluationPersistenceRecord get(long id);

    EvaluationPersistenceRecord findNaturalKey(EvaluationRecordRequest request);

    void insert(EvaluationPersistenceRecord record);

    int update(EvaluationPersistenceRecord record);

    int delete(long id);
}
