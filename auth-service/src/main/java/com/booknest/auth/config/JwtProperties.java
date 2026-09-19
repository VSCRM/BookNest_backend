package com.booknest.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code booknest.jwt.*} properties from application.yml
 * (backed by environment variables in every deployment — see .env.example).
 *
 * @param secret HMAC-SHA256 signing secret. Must be at least 256 bits (32 chars).
 * @param accessTokenMinutes Access token lifetime, in minutes.
 * @param refreshTokenDays Refresh token lifetime, in days.
 */
@ConfigurationProperties(prefix = "booknest.jwt")
public record JwtProperties(
        String secret,
        long accessTokenMinutes,
        long refreshTokenDays
) {
}
