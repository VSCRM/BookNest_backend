package com.booknest.auth.dto;

import java.time.Instant;

/** Published to {@code auth.user-logged-in} after a successful login (any provider). */
public record UserLoggedInEvent(String email, String provider, Instant occurredAt) {
}
