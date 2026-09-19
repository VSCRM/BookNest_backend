package com.booknest.auth;

import com.booknest.auth.config.AppProperties;
import com.booknest.auth.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point of the BookNest authentication microservice.
 *
 * <p>This service is the single source of truth for user identity across
 * the BookNest system: it owns the {@code users} table, handles
 * email/password registration and login, handles Google OAuth2 login, and
 * issues the JWTs that both the Rails backend and the React playground
 * trust to identify the current user.
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, AppProperties.class})
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
