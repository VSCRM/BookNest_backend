package com.booknest.auth.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DtoTest {

    @Test
    void authResponseSuccessFactorySetsSuccessTrueAndNoMessage() {
        UserDto user = new UserDto("jane@booknest.test", "Jane Reader", "user");

        AuthResponse response = AuthResponse.success(user);

        assertThat(response.success()).isTrue();
        assertThat(response.user()).isEqualTo(user);
        assertThat(response.message()).isNull();
    }

    @Test
    void authResponseFailureFactorySetsSuccessFalseAndNoUser() {
        AuthResponse response = AuthResponse.failure("invalid_credentials");

        assertThat(response.success()).isFalse();
        assertThat(response.user()).isNull();
        assertThat(response.message()).isEqualTo("invalid_credentials");
    }

    @Test
    void forgotPasswordResponseSuccessCarriesDevCodeAndNoSentFlag() {
        ForgotPasswordResponse response = ForgotPasswordResponse.success("jane@booknest.test", "123456");

        assertThat(response.success()).isTrue();
        assertThat(response.email()).isEqualTo("jane@booknest.test");
        assertThat(response.sent()).isFalse();
        assertThat(response.devCode()).isEqualTo("123456");
        assertThat(response.message()).isNull();
    }

    @Test
    void forgotPasswordResponseFailureCarriesOnlyMessage() {
        ForgotPasswordResponse response = ForgotPasswordResponse.failure("account_not_found");

        assertThat(response.success()).isFalse();
        assertThat(response.email()).isNull();
        assertThat(response.sent()).isNull();
        assertThat(response.devCode()).isNull();
        assertThat(response.message()).isEqualTo("account_not_found");
    }

    @Test
    void userDtoExposesUsernameNicknameAndRole() {
        UserDto dto = new UserDto("jane@booknest.test", "Jane Reader", "admin");

        assertThat(dto.username()).isEqualTo("jane@booknest.test");
        assertThat(dto.nickname()).isEqualTo("Jane Reader");
        assertThat(dto.role()).isEqualTo("admin");
    }

    @Test
    void requestRecordsExposeGivenValues() {
        assertThat(new LoginRequest("jane@booknest.test", "pw").email()).isEqualTo("jane@booknest.test");
        assertThat(new RegisterRequest("jane@booknest.test", "pw123456", "Jane").name()).isEqualTo("Jane");
        assertThat(new ForgotPasswordRequest("jane@booknest.test").email()).isEqualTo("jane@booknest.test");
        assertThat(new ResetPasswordRequest("jane@booknest.test", "123456", "newpw123").code()).isEqualTo("123456");
        assertThat(new UpdateProfileRequest("New Name", null).name()).isEqualTo("New Name");
    }

    @Test
    void eventRecordsExposeGivenValues() {
        Instant now = Instant.now();
        UserRegisteredEvent registered = new UserRegisteredEvent("jane@booknest.test", "Jane", "LOCAL", now);
        UserLoggedInEvent loggedIn = new UserLoggedInEvent("jane@booknest.test", "LOCAL", now);
        LoginFailedEvent failed = new LoginFailedEvent("jane@booknest.test", "invalid_credentials", now);

        assertThat(registered.email()).isEqualTo("jane@booknest.test");
        assertThat(registered.occurredAt()).isEqualTo(now);
        assertThat(loggedIn.provider()).isEqualTo("LOCAL");
        assertThat(failed.reason()).isEqualTo("invalid_credentials");
    }
}
