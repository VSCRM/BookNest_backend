package com.booknest.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload for {@code POST /api/auth/register}. */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 6, max = 128) String password,
        @NotBlank @Size(min = 1, max = 100) String name
) {
}
