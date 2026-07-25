package com.tongji.enso.mybatisdemo.admin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量管理员 Bearer Token。合并统一登录模块时可以整体替换本类，
 * /admin/** 的鉴权入口无需改变。
 */
@Service
public class AdminTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    @Value("${admin.auth.token-ttl-seconds:28800}")
    private long ttlSeconds;

    public SessionToken issue(String username) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long expiresAt = System.currentTimeMillis() + ttlSeconds * 1000L;
        sessions.put(token, new Session(username, expiresAt));
        return new SessionToken(token, username, expiresAt);
    }

    public boolean isValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (session.expiresAt <= System.currentTimeMillis()) {
            sessions.remove(token);
            return false;
        }
        return true;
    }

    public String usernameOf(String token) {
        Session session = sessions.get(token);
        return session == null ? null : session.username;
    }

    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    private static final class Session {
        private final String username;
        private final long expiresAt;

        private Session(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }

    public static final class SessionToken {
        private final String token;
        private final String username;
        private final long expiresAt;

        public SessionToken(String token, String username, long expiresAt) {
            this.token = token;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }
}
