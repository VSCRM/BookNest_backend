package com.booknest.auth.controller;

import com.booknest.auth.domain.User;
import com.booknest.auth.dto.AuthResponse;
import com.booknest.auth.dto.ForgotPasswordRequest;
import com.booknest.auth.dto.ForgotPasswordResponse;
import com.booknest.auth.dto.LoginRequest;
import com.booknest.auth.dto.RegisterRequest;
import com.booknest.auth.dto.ResetPasswordRequest;
import com.booknest.auth.dto.UpdateProfileRequest;
import com.booknest.auth.dto.UserDto;
import com.booknest.auth.exception.EmailAlreadyRegisteredException;
import com.booknest.auth.exception.InvalidCredentialsException;
import com.booknest.auth.exception.InvalidResetCodeException;
import com.booknest.auth.repository.UserRepository;
import com.booknest.auth.security.CookieUtil;
import com.booknest.auth.service.AuthEventPublisher;
import com.booknest.auth.service.JwtService;
import com.booknest.auth.service.UserService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * REST entry point for the BookNest authentication microservice.
 *
 * <p>Every endpoint replies with HTTP 200 and a JSON body shaped as
 * {@code {success:true, user:{...}}} or {@code {success:false, message}} —
 * expected business failures (bad credentials, duplicate email, …) are
 * caught here rather than surfaced as 4xx, because the React frontend
 * branches on the {@code success} field rather than on HTTP status.
 *
 * <p>Every login/registration outcome is both logged (Log4j2, audit trail)
 * and published to Kafka via {@link AuthEventPublisher} (for downstream
 * consumers such as fraud detection or notifications). Logging never
 * includes raw passwords, password hashes, or full JWTs — only email and
 * outcome.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final Logger log = LogManager.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtService jwtService;
    private final CookieUtil cookieUtil;
    private final UserRepository userRepository;
    private final AuthEventPublisher eventPublisher;

    // Public, unauthenticated, side-effect-free — exists purely so an
    // uptime monitor (e.g. UptimeRobot) can ping this service and keep a
    // free-tier host (Render, etc.) from spinning it down for inactivity.
    // Deliberately outside the {success, user} envelope other endpoints
    // use — a monitor only cares about getting back a 200.
    @GetMapping("/health")
    public String health() {
        return "ok";
    }

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        try {
            User user = userService.register(request.email(), request.password(), request.name());
            issueCookies(user, response);
            log.info("User registered: email={} provider={}", user.getEmail(), user.getProvider());
            eventPublisher.publishUserRegistered(user);
            return AuthResponse.success(toDto(user));
        } catch (EmailAlreadyRegisteredException e) {
            log.warn("Registration rejected, email already in use: email={}", request.email());
            return AuthResponse.failure("email_already_registered");
        }
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        try {
            User user = userService.authenticate(request.email(), request.password());
            issueCookies(user, response);
            log.info("User logged in: email={} provider={}", user.getEmail(), user.getProvider());
            eventPublisher.publishUserLoggedIn(user);
            return AuthResponse.success(toDto(user));
        } catch (InvalidCredentialsException e) {
            log.warn("Login rejected: email={} reason={}", request.email(), e.getMessage());
            eventPublisher.publishLoginFailed(request.email(), e.getMessage());
            return AuthResponse.failure("invalid_credentials");
        }
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        Optional<String> refreshToken = readCookie(request, CookieUtil.REFRESH_COOKIE);
        if (refreshToken.isEmpty()) {
            return AuthResponse.failure("no_refresh_token");
        }

        Optional<Claims> claims = jwtService.parseClaims(refreshToken.get())
                .filter(jwtService::isRefreshToken);
        if (claims.isEmpty()) {
            log.warn("Token refresh rejected: invalid or expired refresh token");
            return AuthResponse.failure("invalid_refresh_token");
        }

        String email = jwtService.extractEmail(claims.get());
        return userRepository.findByEmail(email)
                .map(user -> {
                    issueCookies(user, response);
                    log.debug("Access token refreshed: email={}", user.getEmail());
                    return AuthResponse.success(toDto(user));
                })
                .orElseGet(() -> AuthResponse.failure("account_not_found"));
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        if (authentication == null) {
            return AuthResponse.failure("not_authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .map(user -> AuthResponse.success(toDto(user)))
                .orElseGet(() -> AuthResponse.failure("account_not_found"));
    }

    @PutMapping("/me")
    public AuthResponse updateMe(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        if (authentication == null) {
            return AuthResponse.failure("not_authenticated");
        }
        User user = userService.updateProfile(authentication.getName(), request.name(), request.password());
        log.info("Profile updated: email={}", user.getEmail());
        return AuthResponse.success(toDto(user));
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        try {
            String code = userService.issueResetCode(request.email());
            // No SMTP is wired up in this local/educational deployment — the
            // code is returned directly to the caller instead, matching the
            // frontend's own dev-mode fallback (display the code on-screen).
            log.info("Password reset code issued: email={}", request.email());
            return ForgotPasswordResponse.success(request.email(), code);
        } catch (InvalidCredentialsException e) {
            log.warn("Password reset requested for unknown account: email={}", request.email());
            return ForgotPasswordResponse.failure("account_not_found");
        }
    }

    @PostMapping("/reset-password")
    public AuthResponse resetPassword(@Valid @RequestBody ResetPasswordRequest request, HttpServletResponse response) {
        try {
            User user = userService.resetPassword(request.email(), request.code(), request.newPassword());
            issueCookies(user, response);
            log.info("Password reset completed: email={}", user.getEmail());
            return AuthResponse.success(toDto(user));
        } catch (InvalidResetCodeException e) {
            log.warn("Password reset rejected: email={} reason={}", request.email(), e.getMessage());
            return AuthResponse.failure("invalid_or_expired_code");
        }
    }

    @PostMapping("/logout")
    public AuthResponse logout(Authentication authentication, HttpServletResponse response) {
        cookieUtil.clearAuthCookies(response);
        if (authentication != null) {
            log.info("User logged out: email={}", authentication.getName());
        }
        return new AuthResponse(true, null, null);
    }

    private void issueCookies(User user, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        cookieUtil.writeAuthCookies(response, accessToken, refreshToken);
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return Optional.empty();
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return Optional.ofNullable(cookie.getValue());
        }
        return Optional.empty();
    }

    private UserDto toDto(User user) {
        return new UserDto(user.getEmail(), user.getName(), user.getRole().name().toLowerCase());
    }
}
