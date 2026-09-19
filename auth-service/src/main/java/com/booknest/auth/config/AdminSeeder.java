package com.booknest.auth.config;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import com.booknest.auth.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Function;

/**
 * Seeds one or more admin accounts on startup so logging in through this
 * service (not just the old Rails /rubyback pages) also gets admin
 * access. Mirrors {@code backend/db/seeds.rb} on the Rails side.
 *
 * <p>Two ways to configure it (see auth-service/.env.example):
 * <ul>
 *   <li>{@code ADMIN_ACCOUNTS=email1:password1,email2:password2,...} —
 *       seeds one admin per pair. Preferred when you need more than one.</li>
 *   <li>{@code ADMIN_EMAIL} / {@code ADMIN_PASSWORD} — legacy single-admin
 *       form, still supported for backward compatibility. Ignored if
 *       {@code ADMIN_ACCOUNTS} is set.</li>
 * </ul>
 *
 * <p>Idempotent: if an admin account already exists, its password and
 * role are left untouched on restart (so a manually-changed admin
 * password isn't silently reset every time the service boots).
 */
@Slf4j
@Component
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Function<String, String> env;

    @Autowired
    public AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this(userRepository, passwordEncoder, System::getenv);
    }

    /**
     * Package-private constructor for tests: lets them supply a fixed
     * {@code Map::get}-style lookup instead of statically mocking
     * {@link System}, which Mockito refuses to do.
     */
    AdminSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder, Function<String, String> env) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.env = env;
    }

    @Override
    public void run(String... args) {
        for (String[] pair : resolveAccounts()) {
            seedOne(pair[0].trim().toLowerCase(), pair[1]);
        }
    }

    /** Returns a list of {email, password} pairs to seed. */
    private java.util.List<String[]> resolveAccounts() {
        String accounts = env.apply("ADMIN_ACCOUNTS");
        if (accounts != null && !accounts.isBlank()) {
            java.util.List<String[]> result = new java.util.ArrayList<>();
            for (String entry : accounts.split(",")) {
                if (entry.isBlank()) continue;
                String[] parts = entry.split(":", 2);
                if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                    log.warn("Skipping malformed ADMIN_ACCOUNTS entry (expected email:password): {}", entry);
                    continue;
                }
                result.add(parts);
            }
            if (!result.isEmpty()) return result;
            log.warn("ADMIN_ACCOUNTS was set but had no valid email:password entries — falling back to ADMIN_EMAIL/ADMIN_PASSWORD.");
        }

        // Legacy single-admin fallback.
        String adminEmail = Optional.ofNullable(env.apply("ADMIN_EMAIL")).orElse("admin@booknest.local");
        boolean usingDefaultPassword = env.apply("ADMIN_PASSWORD") == null;
        String adminPassword = Optional.ofNullable(env.apply("ADMIN_PASSWORD")).orElse("admin12345");
        if (usingDefaultPassword) {
            log.warn("ADMIN_PASSWORD is not set — seeding the admin account with the built-in "
                             + "default password. Set ADMIN_EMAIL/ADMIN_PASSWORD (or ADMIN_ACCOUNTS) before "
                             + "deploying anywhere reachable outside local development.");
        }
        return java.util.List.<String[]>of(new String[] {adminEmail, adminPassword});
    }

    private void seedOne(String adminEmail, String adminPassword) {
        if (userRepository.existsByEmail(adminEmail)) {
            log.info("Admin account already exists ({}), skipping seed.", adminEmail);
            return;
        }

        User admin = User.builder()
                             .email(adminEmail)
                             .name("BookNest Admin")
                             .passwordHash(passwordEncoder.encode(adminPassword))
                             .provider(AuthProvider.LOCAL)
                             .role(Role.ADMIN)
                             .build();
        userRepository.save(admin);
        log.info("Seeded admin account: {}", adminEmail);
    }
}
