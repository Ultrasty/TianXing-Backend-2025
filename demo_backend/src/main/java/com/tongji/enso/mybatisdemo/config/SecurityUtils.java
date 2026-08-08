package com.tongji.enso.mybatisdemo.config;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class SecurityUtils {

    /**
     * 生成 BCryptPasswordEncoder 密码密文 (对标 RuoYi-Vue)
     *
     * @param password 明文密码
     * @return 60 位加密密文
     */
    public static String encryptPassword(String password) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(password);
    }

    /**
     * 判断密码是否相同 (对标 RuoYi-Vue)
     *
     * @param rawPassword     真实明文密码
     * @param encodedPassword 数据库加密密文
     * @return 结果
     */
    public static boolean matchesPassword(String rawPassword, String encodedPassword) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
