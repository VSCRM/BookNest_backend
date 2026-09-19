package com.booknest.auth.exception;

/** Thrown when a password-reset code is missing, wrong, or expired. */
public class InvalidResetCodeException extends RuntimeException {
    public InvalidResetCodeException(String message) {
        super(message);
    }
}
