package com.tongji.enso.mybatisdemo.admin.auth;

import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    public static final String ADMIN_USERNAME_ATTRIBUTE = "adminUsername";

    private final JwtTokenService jwtTokenService;
    private final AdminUserMapper adminUserMapper;

    public AdminAuthInterceptor(JwtTokenService jwtTokenService, AdminUserMapper adminUserMapper) {
        this.jwtTokenService = jwtTokenService;
        this.adminUserMapper = adminUserMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AdminException("AUTH_REQUIRED", "需要管理员登录", HttpStatus.UNAUTHORIZED);
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isEmpty()) {
            throw new AdminException("AUTH_REQUIRED", "需要管理员登录", HttpStatus.UNAUTHORIZED);
        }
        String username = jwtTokenService.validateAndGetUsername(token);
        AdminUser user = adminUserMapper.findByUsername(username);
        if (user == null || !user.isEnabled()) {
            throw new AdminException("AUTH_INVALID_TOKEN", "管理员Token对应的账号不可用", HttpStatus.UNAUTHORIZED);
        }
        request.setAttribute(ADMIN_USERNAME_ATTRIBUTE, username);
        return true;
    }
}
