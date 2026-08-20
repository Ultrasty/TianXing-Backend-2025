package com.tongji.enso.mybatisdemo.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtils {
    private static final String ISSUER = "tianxing-admin";

    private final SecretKey signingKey;
    private final long expireSeconds;

    public JwtUtils(@Value("${admin.jwt.secret:}") String configuredSecret,
                    @Value("${admin.jwt.expire-seconds:7200}") long expireSeconds,
                    Environment environment) {
        if (expireSeconds <= 0) {
            throw new IllegalStateException("admin.jwt.expire-seconds must be positive");
        }
        this.signingKey = createSigningKey(configuredSecret, environment);
        this.expireSeconds = expireSeconds;
    }

    public String createToken(String username) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(ISSUER)
                .setSubject(username)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .requireIssuer(ISSUER)
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getUsernameFromToken(String token) {
        try {
            return parseToken(token).getSubject();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration() != null && claims.getExpiration().after(new Date());
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }

    private SecretKey createSigningKey(String configuredSecret, Environment environment) {
        if (configuredSecret == null || configuredSecret.trim().isEmpty()) {
            if (environment.acceptsProfiles(Profiles.of("prod", "production"))) {
                throw new IllegalStateException("ADMIN_JWT_SECRET is required in production profiles");
            }
            byte[] randomSecret = new byte[32];
            new SecureRandom().nextBytes(randomSecret);
            return Keys.hmacShaKeyFor(randomSecret);
        }

        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("ADMIN_JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }
}
