package com.tongji.enso.mybatisdemo.admin.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JDK 原生 PBKDF2 密码哈希，无需额外引入 Spring Security。
 * 优先使用 PBKDF2-HMAC-SHA256；极旧 Java 8 环境不支持时自动回退 HMAC-SHA1。
 * 数据库存储格式：algorithm$iterations$saltBase64$hashBase64
 */
@Component
public class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String hash(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("管理员密码至少需要 8 个字符");
        }
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);
        Algorithm algorithm = preferredAlgorithm();
        byte[] derived = derive(password, salt, ITERATIONS, algorithm.jcaName);
        return algorithm.prefix + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public boolean matches(String password, String encoded) {
        if (password == null || encoded == null) {
            return false;
        }
        try {
            String[] parts = encoded.split("\\$");
            if (parts.length != 4) {
                return false;
            }
            Algorithm algorithm = Algorithm.fromPrefix(parts[0]);
            if (algorithm == null) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            byte[] actual = derive(password, salt, iterations, algorithm.jcaName);
            return MessageDigest.isEqual(expected, actual);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Algorithm preferredAlgorithm() {
        try {
            SecretKeyFactory.getInstance(Algorithm.SHA256.jcaName);
            return Algorithm.SHA256;
        } catch (Exception ignored) {
            return Algorithm.SHA1;
        }
    }

    private byte[] derive(String password, byte[] salt, int iterations, String jcaName) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(jcaName);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("无法计算管理员密码哈希", ex);
        } finally {
            spec.clearPassword();
        }
    }

    private enum Algorithm {
        SHA256("pbkdf2_sha256", "PBKDF2WithHmacSHA256"),
        SHA1("pbkdf2_sha1", "PBKDF2WithHmacSHA1");

        private final String prefix;
        private final String jcaName;

        Algorithm(String prefix, String jcaName) {
            this.prefix = prefix;
            this.jcaName = jcaName;
        }

        private static Algorithm fromPrefix(String prefix) {
            for (Algorithm algorithm : values()) {
                if (algorithm.prefix.equals(prefix)) {
                    return algorithm;
                }
            }
            return null;
        }
    }
}
