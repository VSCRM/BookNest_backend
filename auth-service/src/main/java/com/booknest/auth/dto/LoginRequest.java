package com.booknest.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Payload for {@code POST /api/auth/login}. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
) {
}
