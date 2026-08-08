package com.tongji.enso.mybatisdemo.admin.config;

import com.tongji.enso.mybatisdemo.admin.repository.AdminAuthRepository;
import com.tongji.enso.mybatisdemo.admin.security.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 首次部署时通过环境变量创建第一个管理员：
 * ADMIN_BOOTSTRAP_USERNAME / ADMIN_BOOTSTRAP_PASSWORD。
 * 明文密码不会写入数据库，数据库只保存 PBKDF2 哈希。
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final AdminAuthRepository repository;
    private final PasswordHasher passwordHasher;

    @Value("${admin.bootstrap.username:}")
    private String bootstrapUsername;

    @Value("${admin.bootstrap.password:}")
    private String bootstrapPassword;

    public AdminBootstrapRunner(AdminAuthRepository repository, PasswordHasher passwordHasher) {
        this.repository = repository;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public void run(String... args) {
        repository.ensureTable();
        if (repository.countAdmins() > 0) {
            return;
        }
        if (isBlank(bootstrapUsername) || isBlank(bootstrapPassword)) {
            logger.warn("admin_user 表中还没有管理员。请设置 ADMIN_BOOTSTRAP_USERNAME 和 ADMIN_BOOTSTRAP_PASSWORD 后重启一次服务。\n" +
                    "密码只会以 PBKDF2 哈希保存到数据库中。");
            return;
        }
        repository.insert(bootstrapUsername.trim(), passwordHasher.hash(bootstrapPassword));
        logger.info("已创建首个管理员账号: {}", bootstrapUsername.trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
