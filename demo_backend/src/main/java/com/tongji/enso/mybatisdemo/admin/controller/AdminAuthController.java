package com.tongji.enso.mybatisdemo.admin.controller;

import com.tongji.enso.mybatisdemo.admin.config.AdminAuthInterceptor;
import com.tongji.enso.mybatisdemo.admin.dto.AdminLoginRequest;
import com.tongji.enso.mybatisdemo.admin.security.AdminTokenService;
import com.tongji.enso.mybatisdemo.admin.service.AdminAuthService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {
    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AdminLoginRequest request) {
        AdminTokenService.SessionToken session = authService.login(request.getUsername(), request.getPassword());
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("token", session.getToken());
        result.put("username", session.getUsername());
        result.put("expiresAt", session.getExpiresAt());
        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(AdminAuthInterceptor.extractBearerToken(authorization));
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("message", "已退出登录");
        return result;
    }
}
