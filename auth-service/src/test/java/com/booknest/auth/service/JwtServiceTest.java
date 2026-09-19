package com.booknest.auth.service;

import com.booknest.auth.config.JwtProperties;
import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JwtService}. Access/refresh tokens are generated and
 * parsed with the real jjwt implementation (no mocking) since the whole
 * point of this class is cryptographic correctness.
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef0123456789abcdef"; // > 32 bytes
    private static final long ACCESS_MINUTES = 15;
    private static final long REFRESH_DAYS = 7;

    private JwtProperties properties;
    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties(SECRET, ACCESS_MINUTES, REFRESH_DAYS);
        jwtService = new JwtService(properties);
        user = User.builder()
                .id(1L)
                .email("reader@booknest.test")
                .name("Reader One")
                .provider(AuthProvider.LOCAL)
                .role(Role.ADMIN)
                .build();
    }

    @Nested
    class AccessTokens {

        @Test
        void generatedAccessTokenCarriesExpectedClaims() {
            String token = jwtService.generateAccessToken(user);

            Optional<Claims> parsed = jwtService.parseClaims(token);

            assertThat(parsed).isPresent();
            Claims claims = parsed.get();
            assertThat(claims.getSubject()).isEqualTo("reader@booknest.test");
            assertThat(claims.get("name", String.class)).isEqualTo("Reader One");
            assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
            assertThat(claims.get("typ", String.class)).isEqualTo("access");
        }

        @Test
        void isAccessTokenTrueAndIsRefreshTokenFalse() {
            Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(user)).orElseThrow();

            assertThat(jwtService.isAccessToken(claims)).isTrue();
            assertThat(jwtService.isRefreshToken(claims)).isFalse();
        }

        @Test
        void extractEmailReturnsSubject() {
            Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(user)).orElseThrow();

            assertThat(jwtService.extractEmail(claims)).isEqualTo("reader@booknest.test");
        }

        @Test
        void extractRoleReturnsRoleFromClaim() {
            Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(user)).orElseThrow();

            assertThat(jwtService.extractRole(claims)).isEqualTo(Role.ADMIN);
        }

        @Test
        void accessTokenMaxAgeSecondsConvertsMinutesToSeconds() {
            assertThat(jwtService.accessTokenMaxAgeSeconds()).isEqualTo(ACCESS_MINUTES * 60);
        }
    }

    @Nested
    class RefreshTokens {

        @Test
        void generatedRefreshTokenCarriesExpectedClaims() {
            String token = jwtService.generateRefreshToken(user);

            Claims claims = jwtService.parseClaims(token).orElseThrow();

            assertThat(claims.getSubject()).isEqualTo("reader@booknest.test");
            assertThat(claims.get("typ", String.class)).isEqualTo("refresh");
            // Refresh tokens intentionally omit name/role: they only exist to mint a
            // fresh access token, never to authorize a request directly.
            assertThat(claims.get("name", String.class)).isNull();
        }

        @Test
        void isRefreshTokenTrueAndIsAccessTokenFalse() {
            Claims claims = jwtService.parseClaims(jwtService.generateRefreshToken(user)).orElseThrow();

            assertThat(jwtService.isRefreshToken(claims)).isTrue();
            assertThat(jwtService.isAccessToken(claims)).isFalse();
        }

        @Test
        void extractRoleFallsBackToUserWhenClaimAbsent() {
            Claims claims = jwtService.parseClaims(jwtService.generateRefreshToken(user)).orElseThrow();

            assertThat(jwtService.extractRole(claims)).isEqualTo(Role.USER);
        }

        @Test
        void refreshTokenMaxAgeSecondsConvertsDaysToSeconds() {
            assertThat(jwtService.refreshTokenMaxAgeSeconds()).isEqualTo(REFRESH_DAYS * 24 * 60 * 60);
        }
    }

    @Nested
    class ParsingFailures {

        @Test
        void parseClaimsReturnsEmptyForGarbageInput() {
            assertThat(jwtService.parseClaims("this-is-not-a-jwt")).isEmpty();
        }

        @Test
        void parseClaimsReturnsEmptyForBlankInput() {
            assertThat(jwtService.parseClaims("")).isEmpty();
        }

        @Test
        void parseClaimsReturnsEmptyForExpiredToken() {
            SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
            Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
            String expired = Jwts.builder()
                    .subject("reader@booknest.test")
                    .issuedAt(Date.from(past.minusSeconds(60)))
                    .expiration(Date.from(past))
                    .signWith(key)
                    .compact();

            assertThat(jwtService.parseClaims(expired)).isEmpty();
        }

        @Test
        void parseClaimsReturnsEmptyForTokenSignedWithDifferentKey() {
            SecretKey otherKey = Keys.hmacShaKeyFor("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz".getBytes(StandardCharsets.UTF_8));
            String foreignToken = Jwts.builder()
                    .subject("reader@booknest.test")
                    .issuedAt(new Date())
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .signWith(otherKey)
                    .compact();

            assertThat(jwtService.parseClaims(foreignToken)).isEmpty();
        }
    }
}
