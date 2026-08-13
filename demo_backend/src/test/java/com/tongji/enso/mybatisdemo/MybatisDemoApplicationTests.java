package com.tongji.enso.mybatisdemo;

import com.tongji.enso.mybatisdemo.config.JwtUtils;
import com.tongji.enso.mybatisdemo.entity.admin.AdminUser;
import com.tongji.enso.mybatisdemo.mapper.admin.AdminUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootTest
class MybatisDemoApplicationTests {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void testAdminAuth() {
        System.out.println("=== Testing AdminUserMapper ===");
        AdminUser user = adminUserMapper.findByUsername("admin");
        System.out.println("Found user: " + user);
        if (user != null) {
            System.out.println("Username: " + user.getUsername());
            System.out.println("PasswordHash: " + user.getPasswordHash());
            System.out.println("Enabled: " + user.getEnabled());
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            boolean match = encoder.matches("admin123", user.getPasswordHash());
            System.out.println("BCrypt match admin123: " + match);
            String token = jwtUtils.createToken(user.getUsername());
            System.out.println("Generated JWT Token: " + token);
            System.out.println("JWT Valid: " + jwtUtils.validateToken(token));
        }
    }
}
