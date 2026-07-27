package com.tongji.enso.mybatisdemo.admin.auth;

import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String ISSUER = "tianxing-admin";

    private final SecretKey key;
    private final long expireSeconds;

    public JwtTokenService(@Value("${admin.jwt.secret:}") String configuredSecret,
                           @Value("${admin.jwt.expire-seconds:7200}") long expireSeconds,
                           Environment environment) {
        if (expireSeconds <= 0) {
            throw new IllegalStateException("admin.jwt.expire-seconds must be positive");
        }
        this.expireSeconds = expireSeconds;
        this.key = createKey(configuredSecret, environment);
    }

    private SecretKey createKey(String configuredSecret, Environment environment) {
        if (configuredSecret == null || configuredSecret.trim().isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("ADMIN_JWT_SECRET is required in the prod profile");
            }
            byte[] randomSecret = new byte[32];
            new SecureRandom().nextBytes(randomSecret);
            LOGGER.warn("ADMIN_JWT_SECRET is not set; using an ephemeral development key");
            return Keys.hmacShaKeyFor(randomSecret);
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("ADMIN_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(AdminUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(user.getUsername())
                .claim("adminId", user.getId())
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String validateAndGetUsername(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .requireIssuer(ISSUER)
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (ExpiredJwtException exception) {
            throw new AdminException("AUTH_TOKEN_EXPIRED", "管理员登录已过期", HttpStatus.UNAUTHORIZED);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AdminException("AUTH_INVALID_TOKEN", "管理员Token无效", HttpStatus.UNAUTHORIZED);
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
