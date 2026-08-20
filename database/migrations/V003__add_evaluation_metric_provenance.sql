CREATE TABLE IF NOT EXISTS evaluation_metric_provenance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category VARCHAR(16) NOT NULL,
    record_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    prediction_model VARCHAR(64) NOT NULL,
    observation_dataset VARCHAR(64) NOT NULL,
    observation_version VARCHAR(32) NOT NULL,
    details JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_evaluation_metric_provenance_record (category, record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
