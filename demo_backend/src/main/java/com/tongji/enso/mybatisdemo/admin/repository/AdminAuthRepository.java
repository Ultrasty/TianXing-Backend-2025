package com.tongji.enso.mybatisdemo.admin.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class AdminAuthRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS admin_user (" +
                        "id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY," +
                        "username VARCHAR(64) NOT NULL UNIQUE," +
                        "password_hash VARCHAR(255) NOT NULL," +
                        "enabled TINYINT(1) NOT NULL DEFAULT 1," +
                        "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                        "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    public int countAdmins() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admin_user", Integer.class);
        return count == null ? 0 : count;
    }

    public void insert(String username, String passwordHash) {
        jdbcTemplate.update(
                "INSERT INTO admin_user(username, password_hash, enabled) VALUES (?, ?, 1)",
                username,
                passwordHash
        );
    }

    public AdminCredential findEnabledByUsername(String username) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, username, password_hash FROM admin_user WHERE username = ? AND enabled = 1 LIMIT 1",
                username
        );
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new AdminCredential(
                ((Number) row.get("id")).longValue(),
                String.valueOf(row.get("username")),
                String.valueOf(row.get("password_hash"))
        );
    }

    public static final class AdminCredential {
        private final long id;
        private final String username;
        private final String passwordHash;

        public AdminCredential(long id, String username, String passwordHash) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
        }

        public long getId() {
            return id;
        }

        public String getUsername() {
            return username;
        }

        public String getPasswordHash() {
            return passwordHash;
        }
    }
}
