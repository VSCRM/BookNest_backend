package com.booknest.auth.security;

import com.booknest.auth.config.AppProperties;
import com.booknest.auth.domain.User;
import com.booknest.auth.service.AuthEventPublisher;
import com.booknest.auth.service.JwtService;
import com.booknest.auth.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Runs once Google has confirmed the user's identity. Finds-or-creates the
 * matching BookNest {@link User}, mints this service's own JWTs (Google's
 * token is discarded — everything downstream trusts only our tokens), sets
 * them as cookies, then redirects the browser back to the storefront.
 */
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LogManager.getLogger(OAuth2LoginSuccessHandler.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final AppProperties appProperties;
    private final AuthEventPublisher eventPublisher;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            log.warn("Google login rejected: no email attribute returned by provider");
            response.sendRedirect(appProperties.frontendUrl() + "/login?error=google_no_email");
            return;
        }

        User user = userService.findOrCreateGoogleUser(email, name != null ? name : email);

        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        cookieUtil.writeAuthCookies(response, accessToken, refreshToken);

        log.info("User logged in via Google: email={}", user.getEmail());
        eventPublisher.publishUserLoggedIn(user);

        response.sendRedirect(appProperties.frontendUrl() + resolvePostLoginPath(request));
    }

    /**
     * Where to send the browser after a successful Google login.
     *
     * Prefers whatever {@link CustomAuthorizationRequestResolver} captured
     * off the initial {@code /oauth2/authorization/google?redirect_uri=...}
     * hit — e.g. {@code /cart} to resume a checkout that required signing
     * in first, or {@code /profile} to surface a book that was saved while
     * logged out. Falls back to {@code /profile} (matching the
     * email/password flow's default in useLoginForm.ts) when nothing was
     * captured, e.g. because the person landed here via a direct link
     * rather than the frontend's "Continue with Google" button.
     */
    private String resolvePostLoginPath(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "/profile";
        }

        Object stored = session.getAttribute(
                CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY);
        session.removeAttribute(CustomAuthorizationRequestResolver.POST_LOGIN_REDIRECT_SESSION_KEY);

        String redirect = CustomAuthorizationRequestResolver.sanitize(
                stored instanceof String storedPath ? storedPath : null);
        return redirect != null ? redirect : "/profile";
    }
}
