package com.booknest.auth.dto;

import java.time.Instant;

/** Published to {@code auth.user-registered} after a successful registration. */
public record UserRegisteredEvent(String email, String name, String provider, Instant occurredAt) {
}
