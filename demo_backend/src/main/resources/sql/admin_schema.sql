CREATE TABLE IF NOT EXISTS admin_users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Default account for local development:
-- username: admin
-- password: password
-- Replace this password before deploying.
INSERT INTO admin_users (username, password_hash, enabled)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IjZAgcfl7p92ldGxad68LJZdL17lhWy', 1)
ON DUPLICATE KEY UPDATE
    password_hash = VALUES(password_hash),
    enabled = VALUES(enabled);
