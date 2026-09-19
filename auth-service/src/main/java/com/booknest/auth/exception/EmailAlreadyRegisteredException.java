package com.booknest.auth.exception;

/** Thrown when a registration attempt uses an email that already has an account. */
public class EmailAlreadyRegisteredException extends RuntimeException {
    public EmailAlreadyRegisteredException(String email) {
        super("An account with email " + email + " already exists");
    }
}
