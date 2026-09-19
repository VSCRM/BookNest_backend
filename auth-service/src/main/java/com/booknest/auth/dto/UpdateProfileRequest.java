package com.booknest.auth.dto;

import jakarta.validation.constraints.Size;

/** Payload for {@code PUT /api/auth/me}. Both fields are optional — only present ones are applied. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String name,
        @Size(min = 6, max = 128) String password
) {
}
