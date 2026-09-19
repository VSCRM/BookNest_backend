package com.booknest.auth.security;

import com.booknest.auth.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Reads the {@code booknest_jwt} access-token cookie on every request and,
 * if it is present and valid, populates the Spring Security context with
 * an authenticated principal (the user's email). Missing or invalid tokens
 * are simply ignored here — {@link com.booknest.auth.config.SecurityConfig}
 * decides which endpoints actually require authentication.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws IOException {
        extractAccessToken(request)
                .flatMap(jwtService::parseClaims)
                .filter(jwtService::isAccessToken)
                .ifPresent(this::authenticate);

        try {
            filterChain.doFilter(request, response);
        } catch (jakarta.servlet.ServletException e) {
            throw new IOException(e);
        }
    }

    private void authenticate(Claims claims) {
        String email = jwtService.extractEmail(claims);
        var authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + jwtService.extractRole(claims).name()));
        var authentication = new UsernamePasswordAuthenticationToken(email, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private Optional<String> extractAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (CookieUtil.ACCESS_COOKIE.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
