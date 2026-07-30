package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.entity.admin.AdminUser;
import com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse;
import com.tongji.enso.mybatisdemo.mapper.admin.AdminUserMapper;
import com.tongji.enso.mybatisdemo.service.admin.AdminTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private AdminTokenService adminTokenService;

    @PostMapping("/login")
    public ResponseEntity<AdminApiResponse<Map<String, Object>>> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return new ResponseEntity<>(AdminApiResponse.<Map<String, Object>>fail("请输入用户名和密码"), HttpStatus.BAD_REQUEST);
        }

        AdminUser adminUser = adminUserMapper.findByUsername(request.getUsername().trim());
        if (adminUser == null || !adminUser.isEnabled() || !passwordEncoder.matches(request.getPassword(), adminUser.getPasswordHash())) {
            return new ResponseEntity<>(AdminApiResponse.<Map<String, Object>>fail("用户名或密码错误"), HttpStatus.UNAUTHORIZED);
        }

        String token = adminTokenService.issueToken(adminUser.getUsername());
        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("tokenType", "Bearer");
        response.put("username", adminUser.getUsername());
        return ResponseEntity.ok(AdminApiResponse.ok("登录成功", response));
    }

    @PostMapping("/logout")
    public AdminApiResponse<Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        adminTokenService.invalidate(extractBearerToken(authorization));
        return AdminApiResponse.ok("已退出登录", null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.toLowerCase().startsWith("bearer ")) {
            return value.substring(7).trim();
        }
        return value;
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
