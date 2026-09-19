package com.booknest.auth.service;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.User;
import com.booknest.auth.exception.EmailAlreadyRegisteredException;
import com.booknest.auth.exception.InvalidCredentialsException;
import com.booknest.auth.exception.InvalidResetCodeException;
import com.booknest.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Core identity business logic: registration, login, profile updates and
 * the password-reset flow. Kept independent of HTTP concerns (no
 * {@code HttpServletRequest}/{@code ResponseEntity} here) so it is easy to
 * unit test and to reuse from both {@code AuthController} and the Google
 * OAuth2 success handler.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long RESET_CODE_TTL_MINUTES = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(String email, String rawPassword, String name) {
        String normalizedEmail = normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(normalizedEmail);
        }
        User user = User.builder()
                .email(normalizedEmail)
                .name(name)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .provider(AuthProvider.LOCAL)
                .build();
        return userRepository.save(user);
    }

    public User authenticate(String email, String rawPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (user.getProvider() != AuthProvider.LOCAL || user.getPasswordHash() == null) {
            throw new InvalidCredentialsException(
                    "This account signs in with Google — use the Google login button");
        }
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }
        return user;
    }

    /** Finds an existing Google-linked user or provisions a new one on first login. */
    @Transactional
    public User findOrCreateGoogleUser(String email, String name) {
        String normalizedEmail = normalizeEmail(email);
        return userRepository.findByEmail(normalizedEmail)
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .email(normalizedEmail)
                                .name(name)
                                .provider(AuthProvider.GOOGLE)
                                // Google users never sign in with a local password, but
                                // leaving passwordHash null is a latent footgun: it's an
                                // unset/absent value rather than a real credential, and
                                // anything that forgets the provider==LOCAL check (a new
                                // endpoint, a bug, a bcrypt library that treats null as
                                // "no password required") could turn that into an
                                // authentication bypass. Filling it with a bcrypt hash of
                                // cryptographically random bytes costs nothing, is
                                // unguessable, and means every account always has a real,
                                // non-empty hash on file. authenticate() still rejects
                                // non-LOCAL providers first, so this never becomes a usable
                                // login path for the account owner.
                                .passwordHash(passwordEncoder.encode(randomPassword()))
                                .build()));
    }

    /** 32 bytes (256 bits) of SecureRandom entropy, hex-encoded, for accounts that need a placeholder credential no one will ever type in. */
    private static String randomPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Transactional
    public User updateProfile(String email, String newName, String newRawPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new InvalidCredentialsException("Account not found"));
        if (newName != null && !newName.isBlank()) {
            user.setName(newName);
        }
        if (newRawPassword != null && !newRawPassword.isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(newRawPassword));
            user.setProvider(AuthProvider.LOCAL);
        }
        return userRepository.save(user);
    }

    /** Generates a 6-digit reset code valid for 15 minutes and attaches it to the user. */
    @Transactional
    public String issueResetCode(String email) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new InvalidCredentialsException("Account not found"));
        String code = String.valueOf(100000 + RANDOM.nextInt(900000));
        user.setResetCode(code);
        user.setResetCodeExpiresAt(Instant.now().plusSeconds(RESET_CODE_TTL_MINUTES * 60));
        userRepository.save(user);
        return code;
    }

    @Transactional
    public User resetPassword(String email, String code, String newRawPassword) {
        User user = userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new InvalidResetCodeException("Invalid or expired code"));

        if (user.getResetCode() == null
                || !user.getResetCode().equals(code)
                || user.getResetCodeExpiresAt() == null
                || user.getResetCodeExpiresAt().isBefore(Instant.now())) {
            throw new InvalidResetCodeException("Invalid or expired code");
        }

        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        user.setProvider(AuthProvider.LOCAL);
        user.setResetCode(null);
        user.setResetCodeExpiresAt(null);
        return userRepository.save(user);
    }

    /**
     * Emails are the account identifier and are compared byte-for-byte by
     * {@code UserRepository}, but H2/Postgres string equality is
     * case-sensitive. Without normalizing, "User@Example.com" and
     * "user@example.com" would silently be treated as two different
     * accounts depending on which casing a request happened to use. Every
     * lookup and write in this service goes through this method so the
     * stored email and every comparison stay in a single canonical form.
     */
    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
