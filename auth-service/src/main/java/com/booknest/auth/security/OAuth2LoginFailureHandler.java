package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Sends the user back to the storefront's login page with an error flag on any OAuth2 failure. */
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AppProperties appProperties;

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        // exception.getMessage() can legitimately be null (e.g. some
        // AuthenticationException subclasses never set one), and
        // URLEncoder.encode(null, ...) throws an NPE — fall back to a
        // generic reason instead of turning an OAuth2 failure into a 500.
        String message = exception.getMessage() != null ? exception.getMessage() : "oauth2_login_failed";
        String reason = URLEncoder.encode(message, StandardCharsets.UTF_8);
        response.sendRedirect(appProperties.frontendUrl() + "/login?error=" + reason);
    }
}
