package com.tongji.enso.mybatisdemo.admin.auth;

import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthService {
    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO5g36lQw4wRDrQe8e6b0Qx7u7u4H3o3i";

    private final AdminUserMapper adminUserMapper;
    private final JwtTokenService jwtTokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminUserMapper adminUserMapper, JwtTokenService jwtTokenService) {
        this.adminUserMapper = adminUserMapper;
        this.jwtTokenService = jwtTokenService;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        AdminUser user = adminUserMapper.findByUsername(username);
        String hash = user == null ? DUMMY_HASH : user.getPasswordHash();
        boolean passwordMatches = hash != null && passwordEncoder.matches(request.getPassword(), hash);
        if (user == null || !user.isEnabled() || !passwordMatches) {
            throw new AdminException("AUTH_LOGIN_FAILED", "用户名或密码错误", HttpStatus.UNAUTHORIZED);
        }
        return new LoginResponse(jwtTokenService.createToken(user), jwtTokenService.getExpireSeconds());
    }
}
