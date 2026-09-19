package com.booknest.auth.dto;

/**
 * Envelope returned by every auth endpoint, matching the frontend's
 * discriminated union {@code AuthResultSchema}: either
 * {@code {success:true, user:{...}}} or {@code {success:false, message:"..."}}.
 * Business-logic failures (wrong password, duplicate email, …) are returned
 * this way with HTTP 200 rather than thrown as 4xx — the frontend branches
 * on the {@code success} field, not on HTTP status.
 */
public record AuthResponse(boolean success, UserDto user, String message) {

    public static AuthResponse success(UserDto user) {
        return new AuthResponse(true, user, null);
    }

    public static AuthResponse failure(String message) {
        return new AuthResponse(false, null, message);
    }
}
