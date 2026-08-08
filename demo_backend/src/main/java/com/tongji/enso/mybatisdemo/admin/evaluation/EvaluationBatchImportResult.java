package com.tongji.enso.mybatisdemo.admin.evaluation;

public class EvaluationBatchImportResult {
    private final EvaluationCategory category;
    private final String source;
    private final String mode;
    private final int total;
    private final int inserted;
    private final int updated;

    public EvaluationBatchImportResult(EvaluationCategory category, String source, String mode,
                                       int total, int inserted, int updated) {
        this.category = category;
        this.source = source;
        this.mode = mode;
        this.total = total;
        this.inserted = inserted;
        this.updated = updated;
    }

    public EvaluationCategory getCategory() {
        return category;
    }

    public String getSource() {
        return source;
    }

    public String getMode() {
        return mode;
    }

    public int getTotal() {
        return total;
    }

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }
}
