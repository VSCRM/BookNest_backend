package com.booknest.auth.exception;

import com.booknest.auth.dto.AuthResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Catches anything AuthController did not already handle locally:
 * Bean Validation failures on request bodies, the rare concurrent-registration
 * race on the {@code users.email} unique constraint, and any other
 * unexpected exception. Kept in the same 200-OK-with-envelope shape as the
 * rest of the API for consistency with the frontend's discriminated-union
 * parsing.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LogManager.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public AuthResponse handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("invalid_request");
        return AuthResponse.failure(message);
    }

    /**
     * {@link com.booknest.auth.service.UserService#register} checks
     * {@code existsByEmail} before saving, so this only fires when two
     * registrations for the same email race each other — the losing request
     * hits the {@code users.email} unique constraint instead of the
     * friendlier {@link EmailAlreadyRegisteredException} check.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public AuthResponse handleDuplicateEmailRace(DataIntegrityViolationException ex) {
        log.warn("Registration rejected by database unique constraint (concurrent registration): {}", ex.getMessage());
        return AuthResponse.failure("email_already_registered");
    }

    @ExceptionHandler(Exception.class)
    public AuthResponse handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling auth request", ex);
        return AuthResponse.failure("internal_error");
    }
}
