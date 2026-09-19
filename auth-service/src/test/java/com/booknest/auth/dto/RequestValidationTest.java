package com.booknest.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void registerRequestAcceptsValidPayload() {
        RegisterRequest request = new RegisterRequest("jane@booknest.test", "s3cret!!", "Jane Reader");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void registerRequestRejectsMalformedEmail() {
        RegisterRequest request = new RegisterRequest("not-an-email", "s3cret!!", "Jane Reader");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("email"));
    }

    @Test
    void registerRequestRejectsShortPassword() {
        RegisterRequest request = new RegisterRequest("jane@booknest.test", "abc", "Jane Reader");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("password"));
    }

    @Test
    void registerRequestRejectsBlankName() {
        RegisterRequest request = new RegisterRequest("jane@booknest.test", "s3cret!!", " ");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("name"));
    }

    @Test
    void loginRequestRequiresEmailAndPassword() {
        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(new LoginRequest("", ""));

        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    void loginRequestAcceptsValidPayload() {
        assertThat(validator.validate(new LoginRequest("jane@booknest.test", "anything"))).isEmpty();
    }

    @Test
    void forgotPasswordRequestRejectsBlankEmail() {
        assertThat(validator.validate(new ForgotPasswordRequest(" "))).isNotEmpty();
    }

    @Test
    void resetPasswordRequestRejectsShortNewPassword() {
        Set<ConstraintViolation<ResetPasswordRequest>> violations =
                validator.validate(new ResetPasswordRequest("jane@booknest.test", "123456", "short"));

        assertThat(violations).anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("newPassword"));
    }

    @Test
    void updateProfileRequestAllowsBothFieldsNull() {
        assertThat(validator.validate(new UpdateProfileRequest(null, null))).isEmpty();
    }

    @Test
    void updateProfileRequestRejectsTooShortPasswordWhenProvided() {
        Set<ConstraintViolation<UpdateProfileRequest>> violations =
                validator.validate(new UpdateProfileRequest(null, "abc"));

        assertThat(violations).isNotEmpty();
    }
}
