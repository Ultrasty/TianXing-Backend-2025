package com.tongji.enso.mybatisdemo.admin;

import com.tongji.enso.mybatisdemo.admin.auth.AdminUser;
import com.tongji.enso.mybatisdemo.admin.auth.JwtTokenService;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTests {
    @Test
    void reportsExpiredTokensWithStableErrorCode() throws Exception {
        JwtTokenService service = new JwtTokenService(
                "test-only-expiration-secret-at-least-32-bytes", 1, new MockEnvironment());
        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("expiration-test");
        String token = service.createToken(user);

        Thread.sleep(1200L);

        assertThatThrownBy(() -> service.validateAndGetUsername(token))
                .isInstanceOf(AdminException.class)
                .extracting("code").isEqualTo("AUTH_TOKEN_EXPIRED");
    }
}
