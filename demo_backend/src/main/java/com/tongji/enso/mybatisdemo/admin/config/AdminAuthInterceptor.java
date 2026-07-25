package com.tongji.enso.mybatisdemo.admin.config;

import com.tongji.enso.mybatisdemo.admin.security.AdminTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private final AdminTokenService tokenService;

    public AdminAuthInterceptor(AdminTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = extractBearerToken(request.getHeader("Authorization"));
        if (!tokenService.isValid(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"管理员登录已失效或未登录\"}");
            return false;
        }
        request.setAttribute("adminUsername", tokenService.usernameOf(token));
        return true;
    }

    public static String extractBearerToken(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return null;
        }
        return authorization.substring(prefix.length()).trim();
    }
}
