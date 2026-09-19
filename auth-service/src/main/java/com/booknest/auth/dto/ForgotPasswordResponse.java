package com.booknest.auth.dto;

/**
 * Envelope for {@code POST /api/auth/forgot-password}, matching the
 * frontend's {@code ForgotPasswordResponseSchema}. This service intentionally
 * never sends email itself — it only generates and stores the reset code,
 * returning it here as {@code devCode}. Actually delivering the code by
 * email is the frontend's responsibility (via EmailJS, in authService.ts's
 * apiAuth.forgotPassword), which overwrites {@code sent}/{@code devCode}
 * on its side once it knows whether the send succeeded. {@code sent} is
 * always {@code false} here — that's not a bug, it's this service saying
 * "I haven't emailed anything, here's the code to do it yourself."
 */
public record ForgotPasswordResponse(
        boolean success,
        String email,
        Boolean sent,
        String devCode,
        String message
) {

    public static ForgotPasswordResponse success(String email, String devCode) {
        return new ForgotPasswordResponse(true, email, false, devCode, null);
    }

    public static ForgotPasswordResponse failure(String message) {
        return new ForgotPasswordResponse(false, null, null, null, message);
    }
}
