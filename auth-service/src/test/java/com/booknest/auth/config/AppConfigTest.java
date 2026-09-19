package com.booknest.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class AppConfigTest {

    @Test
    void passwordEncoderBeanIsBCrypt() {
        AppConfig config = new AppConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        String encoded = encoder.encode("s3cret!!");
        assertThat(encoder.matches("s3cret!!", encoded)).isTrue();
        assertThat(encoder.matches("wrong", encoded)).isFalse();
    }
}
