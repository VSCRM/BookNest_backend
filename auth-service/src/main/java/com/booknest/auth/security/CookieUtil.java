package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import com.booknest.auth.service.JwtService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds and writes the two cookies this service issues. No {@code Domain}
 * attribute is set on purpose: a host-only cookie for "localhost" is sent
 * on every port of that host, which is exactly what lets the Rails app
 * (:8080) and the storefront (:5174) both read a cookie set by this
 * service (:9000) during local development without any extra CORS dance.
 * In a real deployment this would move to a shared parent domain instead.
 */
@Component
@RequiredArgsConstructor
public class CookieUtil {

    public static final String ACCESS_COOKIE = "booknest_jwt";
    public static final String REFRESH_COOKIE = "booknest_refresh";

    private final JwtService jwtService;
    private final AppProperties appProperties;

    public void writeAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
        response.addHeader("Set-Cookie", buildCookie(ACCESS_COOKIE, accessToken, "/", jwtService.accessTokenMaxAgeSeconds()).toString());
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, refreshToken, "/api/auth/refresh", jwtService.refreshTokenMaxAgeSeconds()).toString());
    }

    public void clearAuthCookies(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildCookie(ACCESS_COOKIE, "", "/", 0).toString());
        response.addHeader("Set-Cookie", buildCookie(REFRESH_COOKIE, "", "/api/auth/refresh", 0).toString());
    }

    private ResponseCookie buildCookie(String name, String value, String path, long maxAgeSeconds) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(appProperties.cookieSecure()) // driven by booknest.app.cookie-secure; must be true behind HTTPS
                .path(path)
                .maxAge(maxAgeSeconds)
                .sameSite(appProperties.cookieSameSite())
                .build();
    }
}
