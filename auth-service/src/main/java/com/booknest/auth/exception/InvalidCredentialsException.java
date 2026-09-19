package com.booknest.auth.exception;

/** Thrown when login credentials do not match, or the account has no local password. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
