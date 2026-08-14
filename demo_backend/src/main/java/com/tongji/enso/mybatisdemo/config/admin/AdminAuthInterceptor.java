package com.tongji.enso.mybatisdemo.config.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.config.JwtUtils;
import com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse;
import com.tongji.enso.mybatisdemo.service.admin.AdminTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final AdminTokenService adminTokenService;
    private final ObjectMapper objectMapper;
    private final JwtUtils jwtUtils;

    @Autowired
    public AdminAuthInterceptor(AdminTokenService adminTokenService, ObjectMapper objectMapper, JwtUtils jwtUtils) {
        this.adminTokenService = adminTokenService;
        this.objectMapper = objectMapper;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractBearerToken(request.getHeader("Authorization"));
        if ((token != null && jwtUtils.validateToken(token)) || adminTokenService.isValid(token)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(
                AdminApiResponse.fail("管理员登录已失效，请重新登录")
        ));
        return false;
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
}
