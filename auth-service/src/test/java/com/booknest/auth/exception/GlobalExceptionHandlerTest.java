package com.booknest.auth.exception;

import com.booknest.auth.dto.AuthResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void validationFailureReturnsFirstFieldError() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        given(ex.getBindingResult()).willReturn(bindingResult);
        given(bindingResult.getFieldErrors()).willReturn(List.of(
                new FieldError("registerRequest", "email", "must be a well-formed email address"),
                new FieldError("registerRequest", "password", "size must be between 6 and 128")));

        AuthResponse response = handler.handleValidation(ex);

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("email: must be a well-formed email address");
    }

    @Test
    void validationFailureWithNoFieldErrorsFallsBackToGenericMessage() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        given(ex.getBindingResult()).willReturn(bindingResult);
        given(bindingResult.getFieldErrors()).willReturn(List.of());

        AuthResponse response = handler.handleValidation(ex);

        assertThat(response.message()).isEqualTo("invalid_request");
    }

    @Test
    void duplicateEmailRaceIsTranslatedToFriendlyMessage() {
        AuthResponse response = handler.handleDuplicateEmailRace(new DataIntegrityViolationException("unique constraint violated"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("email_already_registered");
    }

    @Test
    void unexpectedExceptionReturnsGenericInternalError() {
        AuthResponse response = handler.handleUnexpected(new RuntimeException("boom"));

        assertThat(response.success()).isFalse();
        assertThat(response.message()).isEqualTo("internal_error");
    }
}
