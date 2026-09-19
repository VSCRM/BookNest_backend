package com.booknest.auth.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionMessagesTest {

    @Test
    void emailAlreadyRegisteredExceptionIncludesEmailInMessage() {
        EmailAlreadyRegisteredException ex = new EmailAlreadyRegisteredException("jane@booknest.test");

        assertThat(ex.getMessage()).contains("jane@booknest.test");
    }

    @Test
    void invalidCredentialsExceptionKeepsGivenMessage() {
        InvalidCredentialsException ex = new InvalidCredentialsException("Invalid email or password");

        assertThat(ex.getMessage()).isEqualTo("Invalid email or password");
    }

    @Test
    void invalidResetCodeExceptionKeepsGivenMessage() {
        InvalidResetCodeException ex = new InvalidResetCodeException("Invalid or expired code");

        assertThat(ex.getMessage()).isEqualTo("Invalid or expired code");
    }
}
