package com.tongji.enso.mybatisdemo.admin.service;

import com.tongji.enso.mybatisdemo.admin.repository.AdminAuthRepository;
import com.tongji.enso.mybatisdemo.admin.security.AdminTokenService;
import com.tongji.enso.mybatisdemo.admin.security.PasswordHasher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminAuthService {
    private final AdminAuthRepository repository;
    private final PasswordHasher passwordHasher;
    private final AdminTokenService tokenService;

    public AdminAuthService(AdminAuthRepository repository,
                            PasswordHasher passwordHasher,
                            AdminTokenService tokenService) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
        this.tokenService = tokenService;
    }

    public AdminTokenService.SessionToken login(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }
        AdminAuthRepository.AdminCredential credential = repository.findEnabledByUsername(username.trim());
        if (credential == null || !passwordHasher.matches(password, credential.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        return tokenService.issue(credential.getUsername());
    }

    public void logout(String token) {
        tokenService.revoke(token);
    }
}
