package com.booknest.auth.service;

import com.booknest.auth.config.JwtProperties;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates the JWTs that identify a BookNest user across all
 * three services (this service, the Rails backend, the React storefront).
 * Uses HMAC-SHA256 with a shared secret — every consumer that needs to
 * verify a token must be configured with the exact same
 * {@code booknest.jwt.secret} value.
 */
@Service
public class JwtService {

    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /** Short-lived token that grants API access; carries name + role for convenience. */
    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                       .subject(user.getEmail())
                       .claim(CLAIM_NAME, user.getName())
                       .claim(CLAIM_ROLE, user.getRole().name())
                       .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                       .issuedAt(Date.from(now))
                       .expiration(Date.from(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES)))
                       .signWith(signingKey, Jwts.SIG.HS256)
                       .compact();
    }

    /** Long-lived token whose only purpose is minting a new access token via /refresh. */
    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                       .subject(user.getEmail())
                       .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                       .issuedAt(Date.from(now))
                       .expiration(Date.from(now.plus(properties.refreshTokenDays(), ChronoUnit.DAYS)))
                       .signWith(signingKey, Jwts.SIG.HS256)
                       .compact();
    }

    /** Parses and verifies a token's signature and expiry. Empty if invalid/expired/malformed. */
    public Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                                    .verifyWith(signingKey)
                                    .build()
                                    .parseSignedClaims(token)
                                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public String extractEmail(Claims claims) {
        return claims.getSubject();
    }

    public Role extractRole(Claims claims) {
        String role = claims.get(CLAIM_ROLE, String.class);
        return role != null ? Role.valueOf(role) : Role.USER;
    }

    public long accessTokenMaxAgeSeconds() {
        return properties.accessTokenMinutes() * 60;
    }

    public long refreshTokenMaxAgeSeconds() {
        return properties.refreshTokenDays() * 24 * 60 * 60;
    }
}
