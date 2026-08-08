package com.tongji.enso.mybatisdemo.service.admin;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class AdminTokenService {
    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final ConcurrentMap<String, AdminSession> sessions = new ConcurrentHashMap<>();

    public String issueToken(String username) {
        cleanupExpiredTokens();
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new AdminSession(username, Instant.now().plus(TOKEN_TTL)));
        return token;
    }

    public boolean isValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        AdminSession session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public void invalidate(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private void cleanupExpiredTokens() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, AdminSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AdminSession> entry = iterator.next();
            if (entry.getValue().expiresAt.isBefore(now)) {
                iterator.remove();
            }
        }
    }

    private static class AdminSession {
        private final String username;
        private final Instant expiresAt;

        private AdminSession(String username, Instant expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
