package com.booknest.auth.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationPropertiesRecordsTest {

    @Test
    void jwtPropertiesExposesGivenValues() {
        JwtProperties properties = new JwtProperties("secret-value", 15L, 7L);

        assertThat(properties.secret()).isEqualTo("secret-value");
        assertThat(properties.accessTokenMinutes()).isEqualTo(15L);
        assertThat(properties.refreshTokenDays()).isEqualTo(7L);
    }

    @Test
    void appPropertiesExposesGivenValues() {
        AppProperties properties = new AppProperties(
                "http://localhost:5174", List.of("http://localhost:5174"), true, "None");

        assertThat(properties.frontendUrl()).isEqualTo("http://localhost:5174");
        assertThat(properties.corsAllowedOrigins()).containsExactly("http://localhost:5174");
        assertThat(properties.cookieSecure()).isTrue();
        assertThat(properties.cookieSameSite()).isEqualTo("None");
    }
}
