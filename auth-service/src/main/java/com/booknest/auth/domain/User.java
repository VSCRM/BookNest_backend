package com.booknest.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A registered BookNest identity. This service is the single source of
 * truth for identity: the Rails backend and the React storefront trust the
 * JWT this service issues rather than storing credentials themselves.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    /**
     * bcrypt hash. For LOCAL accounts this is a hash of the user's real
     * password. For GOOGLE accounts it's a hash of a SecureRandom
     * placeholder the user never sees or types (see
     * {@code UserService#findOrCreateGoogleUser}) — kept non-null so every
     * row always has a real credential on file rather than an unset value,
     * even though {@code authenticate()} rejects non-LOCAL providers
     * before this field is ever checked.
     */
    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.USER;

    /** One-time password-reset code, cleared once used or expired. */
    @Column(name = "reset_code")
    private String resetCode;

    @Column(name = "reset_code_expires_at")
    private Instant resetCodeExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }
}
