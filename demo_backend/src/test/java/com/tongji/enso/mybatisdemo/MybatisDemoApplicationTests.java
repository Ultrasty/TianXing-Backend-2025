package com.tongji.enso.mybatisdemo;

import com.tongji.enso.mybatisdemo.config.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MybatisDemoApplicationTests {

    @Autowired
    private JwtUtils jwtUtils;

    @Test
    void createsAndValidatesJwtWithoutLoggingSensitiveValues() {
        String token = jwtUtils.createToken("test-admin");

        assertThat(jwtUtils.validateToken(token)).isTrue();
        assertThat(jwtUtils.getUsernameFromToken(token)).isEqualTo("test-admin");
    }
}
