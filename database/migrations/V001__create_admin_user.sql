-- Apply this migration to the existing `web` database before enabling the admin API.
-- No default administrator is inserted: generate a BCrypt hash with the bundled tool.
CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Example after generating a hash (replace both placeholders manually):
-- INSERT INTO admin_user(username, password_hash, enabled) VALUES('<username>', '<bcrypt-hash>', 1);
