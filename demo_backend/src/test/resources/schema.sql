DROP TABLE IF EXISTS admin_users;
DROP TABLE IF EXISTS admin_user;
DROP TABLE IF EXISTS evaluation_metric_provenance;
DROP TABLE IF EXISTS info_sic_latlon;
DROP TABLE IF EXISTS obs_enso;
DROP TABLE IF EXISTS tj_nao;
DROP TABLE IF EXISTS tj_sic;
DROP TABLE IF EXISTS tj_sie;

CREATE TABLE admin_users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO admin_users (username, password_hash, enabled) VALUES
('admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', 1);

CREATE TABLE obs_enso (
    id INT AUTO_INCREMENT PRIMARY KEY,
    year VARCHAR(45),
    data VARCHAR(100000)
);

CREATE TABLE tj_nao (
    id INT AUTO_INCREMENT PRIMARY KEY,
    year VARCHAR(45) NOT NULL,
    month VARCHAR(45) NOT NULL,
    data VARCHAR(100000) NOT NULL,
    var_model VARCHAR(45) NOT NULL
);

CREATE TABLE tj_sic (
    id INT AUTO_INCREMENT PRIMARY KEY,
    year VARCHAR(45) NOT NULL,
    month VARCHAR(45) NOT NULL,
    day VARCHAR(45) NOT NULL,
    var_model VARCHAR(45) NOT NULL,
    data VARCHAR(100000),
    CONSTRAINT ck_sic_test_rollback CHECK (data <> '[999]')
);

CREATE TABLE tj_sie (
    id INT AUTO_INCREMENT PRIMARY KEY,
    year VARCHAR(45) NOT NULL,
    month VARCHAR(45) NOT NULL,
    var_model VARCHAR(45) NOT NULL,
    data VARCHAR(100000)
);

CREATE TABLE info_sic_latlon (
    id INT AUTO_INCREMENT PRIMARY KEY,
    lat VARCHAR(100000) NOT NULL,
    lon VARCHAR(100000) NOT NULL
);

CREATE TABLE evaluation_metric_provenance (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(16) NOT NULL,
    record_id BIGINT NOT NULL,
    source VARCHAR(32) NOT NULL,
    prediction_model VARCHAR(64) NOT NULL,
    observation_dataset VARCHAR(64) NOT NULL,
    observation_version VARCHAR(32) NOT NULL,
    details VARCHAR(100000) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (category, record_id)
);
