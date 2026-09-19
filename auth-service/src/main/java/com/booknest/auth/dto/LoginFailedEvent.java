package com.booknest.auth.dto;

import java.time.Instant;

/**
 * Published to {@code auth.login-failed} on any rejected login attempt.
 * Consumed downstream for security auditing / brute-force alerting —
 * never carries the attempted password.
 */
public record LoginFailedEvent(String email, String reason, Instant occurredAt) {
}
