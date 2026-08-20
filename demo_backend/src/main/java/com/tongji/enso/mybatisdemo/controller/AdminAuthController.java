package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.admin.common.AdminApiResponse;
import com.tongji.enso.mybatisdemo.config.JwtUtils;
import com.tongji.enso.mybatisdemo.entity.admin.AdminUser;
import com.tongji.enso.mybatisdemo.mapper.admin.AdminUserMapper;
import com.tongji.enso.mybatisdemo.service.admin.AdminTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final AdminUserMapper adminUserMapper;
    private final AdminTokenService adminTokenService;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthController(AdminUserMapper adminUserMapper, AdminTokenService adminTokenService,
                               JwtUtils jwtUtils) {
        this.adminUserMapper = adminUserMapper;
        this.adminTokenService = adminTokenService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<AdminApiResponse<?>> login(@RequestBody LoginRequest request) {
        if (request == null || isBlank(request.getUsername()) || isBlank(request.getPassword())) {
            return failure(HttpStatus.BAD_REQUEST, "AUTH_LOGIN_FAILED", "请输入用户名和密码");
        }

        AdminUser adminUser = adminUserMapper.findByUsername(request.getUsername().trim());
        if (adminUser == null || !adminUser.isEnabled()
                || !passwordEncoder.matches(request.getPassword(), adminUser.getPasswordHash())) {
            return failure(HttpStatus.UNAUTHORIZED, "AUTH_LOGIN_FAILED", "用户名或密码错误");
        }

        String token = jwtUtils.createToken(adminUser.getUsername());
        adminTokenService.issueTokenWithValue(adminUser.getUsername(), token);
        return ResponseEntity.ok(AdminApiResponse.success(
                new LoginResult(token, adminUser.getUsername(), jwtUtils.getExpireSeconds())));
    }

    @PostMapping("/logout")
    public AdminApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        adminTokenService.invalidate(extractBearerToken(authorization));
        return AdminApiResponse.success(null);
    }

    private ResponseEntity<AdminApiResponse<?>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(AdminApiResponse.failure(code, message));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return value.substring("Bearer ".length()).trim();
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

    public static class LoginResult {
        private final String token;
        private final String tokenType = "Bearer";
        private final String username;
        private final long expiresIn;

        public LoginResult(String token, String username, long expiresIn) {
            this.token = token;
            this.username = username;
            this.expiresIn = expiresIn;
        }

        public String getToken() {
            return token;
        }

        public String getTokenType() {
            return tokenType;
        }

        public String getUsername() {
            return username;
        }

        public long getExpiresIn() {
            return expiresIn;
        }
    }
}
