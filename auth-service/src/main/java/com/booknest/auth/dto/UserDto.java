package com.booknest.auth.dto;

/**
 * Public-facing user shape, matching the frontend's {@code UserSchema}:
 * {@code username} is the email (primary identifier), {@code nickname} is
 * the display name, {@code role} is the lowercase {@link com.booknest.auth.domain.Role}
 * name ({@code "user"}/{@code "admin"}) so the React SPA can gate the admin
 * panel (see {@code AdminBooksPage}) without decoding the JWT itself.
 * Never includes the password hash.
 */
public record UserDto(String username, String nickname, String role) {
}
