package com.booknest.auth.config;

import com.booknest.auth.security.JwtAuthenticationFilter;
import com.booknest.auth.security.OAuth2LoginFailureHandler;
import com.booknest.auth.security.OAuth2LoginSuccessHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    @Mock
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @Mock
    private OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    @Mock
    private OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Test
    void corsConfigurationSourceReflectsConfiguredOrigins() {
        AppProperties appProperties = new AppProperties(
                "http://localhost:5174",
                List.of("https://shop.booknest.example", "https://playground.booknest.example"),
                true,
                "None");
        SecurityConfig securityConfig = new SecurityConfig(
                jwtAuthenticationFilter, oAuth2LoginSuccessHandler, oAuth2LoginFailureHandler, appProperties);

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("https://shop.booknest.example", "https://playground.booknest.example");
        assertThat(configuration.getAllowedMethods())
                .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactly("*");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
