package com.booknest.auth.controller;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import com.booknest.auth.dto.AuthResponse;
import com.booknest.auth.dto.ForgotPasswordRequest;
import com.booknest.auth.dto.ForgotPasswordResponse;
import com.booknest.auth.dto.LoginRequest;
import com.booknest.auth.dto.RegisterRequest;
import com.booknest.auth.dto.ResetPasswordRequest;
import com.booknest.auth.dto.UpdateProfileRequest;
import com.booknest.auth.exception.EmailAlreadyRegisteredException;
import com.booknest.auth.exception.InvalidCredentialsException;
import com.booknest.auth.exception.InvalidResetCodeException;
import com.booknest.auth.repository.UserRepository;
import com.booknest.auth.security.CookieUtil;
import com.booknest.auth.service.AuthEventPublisher;
import com.booknest.auth.service.JwtService;
import com.booknest.auth.service.UserService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private JwtService jwtService;
    @Mock
    private CookieUtil cookieUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthEventPublisher eventPublisher;
    @Mock
    private Claims claims;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService, jwtService, cookieUtil, userRepository, eventPublisher);
    }

    private static User user() {
        return User.builder()
                .id(1L)
                .email("jane@booknest.test")
                .name("Jane Reader")
                .provider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
    }

    @Nested
    class Register {

        @Test
        void returnsSuccessAndIssuesCookiesOnSuccessfulRegistration() {
            RegisterRequest request = new RegisterRequest("jane@booknest.test", "s3cret!!", "Jane Reader");
            User user = user();
            given(userService.register("jane@booknest.test", "s3cret!!", "Jane Reader")).willReturn(user);
            given(jwtService.generateAccessToken(user)).willReturn("access");
            given(jwtService.generateRefreshToken(user)).willReturn("refresh");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.register(request, response);

            assertThat(result.success()).isTrue();
            assertThat(result.user().username()).isEqualTo("jane@booknest.test");
            verify(cookieUtil).writeAuthCookies(response, "access", "refresh");
            verify(eventPublisher).publishUserRegistered(user);
        }

        @Test
        void returnsFailureWhenEmailAlreadyRegistered() {
            RegisterRequest request = new RegisterRequest("jane@booknest.test", "s3cret!!", "Jane Reader");
            given(userService.register(any(), any(), any())).willThrow(new EmailAlreadyRegisteredException("jane@booknest.test"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.register(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("email_already_registered");
            verify(cookieUtil, never()).writeAuthCookies(any(), any(), any());
            verify(eventPublisher, never()).publishUserRegistered(any());
        }
    }

    @Nested
    class Login {

        @Test
        void returnsSuccessAndIssuesCookiesOnValidCredentials() {
            LoginRequest request = new LoginRequest("jane@booknest.test", "s3cret!!");
            User user = user();
            given(userService.authenticate("jane@booknest.test", "s3cret!!")).willReturn(user);
            given(jwtService.generateAccessToken(user)).willReturn("access");
            given(jwtService.generateRefreshToken(user)).willReturn("refresh");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.login(request, response);

            assertThat(result.success()).isTrue();
            verify(eventPublisher).publishUserLoggedIn(user);
        }

        @Test
        void returnsFailureAndPublishesLoginFailedOnInvalidCredentials() {
            LoginRequest request = new LoginRequest("jane@booknest.test", "wrong");
            given(userService.authenticate("jane@booknest.test", "wrong"))
                    .willThrow(new InvalidCredentialsException("Invalid email or password"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.login(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("invalid_credentials");
            verify(eventPublisher).publishLoginFailed("jane@booknest.test", "Invalid email or password");
            verify(cookieUtil, never()).writeAuthCookies(any(), any(), any());
        }
    }

    @Nested
    class Refresh {

        @Test
        void returnsFailureWhenNoRefreshCookiePresent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("no_refresh_token");
        }

        @Test
        void returnsFailureWhenCookiesPresentButNoneMatchRefreshCookieName() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie("some_other_cookie", "irrelevant"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("no_refresh_token");
        }

        @Test
        void returnsFailureWhenCookiePresentButNotAValidRefreshToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie(CookieUtil.REFRESH_COOKIE, "garbage"));
            given(jwtService.parseClaims("garbage")).willReturn(Optional.empty());
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("invalid_refresh_token");
        }

        @Test
        void returnsFailureWhenTokenIsValidButNotARefreshToken() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie(CookieUtil.REFRESH_COOKIE, "access-token-in-wrong-slot"));
            given(jwtService.parseClaims("access-token-in-wrong-slot")).willReturn(Optional.of(claims));
            given(jwtService.isRefreshToken(claims)).willReturn(false);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.message()).isEqualTo("invalid_refresh_token");
        }

        @Test
        void returnsFailureWhenAccountNoLongerExists() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie(CookieUtil.REFRESH_COOKIE, "valid-refresh"));
            given(jwtService.parseClaims("valid-refresh")).willReturn(Optional.of(claims));
            given(jwtService.isRefreshToken(claims)).willReturn(true);
            given(jwtService.extractEmail(claims)).willReturn("ghost@booknest.test");
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.message()).isEqualTo("account_not_found");
        }

        @Test
        void issuesFreshCookiesWhenRefreshTokenIsValid() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new jakarta.servlet.http.Cookie(CookieUtil.REFRESH_COOKIE, "valid-refresh"));
            User user = user();
            given(jwtService.parseClaims("valid-refresh")).willReturn(Optional.of(claims));
            given(jwtService.isRefreshToken(claims)).willReturn(true);
            given(jwtService.extractEmail(claims)).willReturn("jane@booknest.test");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(jwtService.generateAccessToken(user)).willReturn("new-access");
            given(jwtService.generateRefreshToken(user)).willReturn("new-refresh");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.refresh(request, response);

            assertThat(result.success()).isTrue();
            verify(cookieUtil).writeAuthCookies(response, "new-access", "new-refresh");
        }
    }

    @Nested
    class Me {

        @Test
        void returnsFailureWhenNotAuthenticated() {
            AuthResponse result = controller.me(null);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("not_authenticated");
        }

        @Test
        void returnsUserWhenAuthenticatedAndFound() {
            Authentication authentication = new TestingAuthenticationToken("jane@booknest.test", null);
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user()));

            AuthResponse result = controller.me(authentication);

            assertThat(result.success()).isTrue();
            assertThat(result.user().username()).isEqualTo("jane@booknest.test");
        }

        @Test
        void returnsFailureWhenAuthenticatedButAccountDeleted() {
            Authentication authentication = new TestingAuthenticationToken("ghost@booknest.test", null);
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());

            AuthResponse result = controller.me(authentication);

            assertThat(result.message()).isEqualTo("account_not_found");
        }
    }

    @Nested
    class UpdateMe {

        @Test
        void returnsFailureWhenNotAuthenticated() {
            UpdateProfileRequest request = new UpdateProfileRequest("New Name", null);

            AuthResponse result = controller.updateMe(null, request);

            assertThat(result.message()).isEqualTo("not_authenticated");
        }

        @Test
        void updatesProfileWhenAuthenticated() {
            Authentication authentication = new TestingAuthenticationToken("jane@booknest.test", null);
            UpdateProfileRequest request = new UpdateProfileRequest("New Name", null);
            User updated = User.builder().email("jane@booknest.test").name("New Name").provider(AuthProvider.LOCAL).build();
            given(userService.updateProfile("jane@booknest.test", "New Name", null)).willReturn(updated);

            AuthResponse result = controller.updateMe(authentication, request);

            assertThat(result.success()).isTrue();
            assertThat(result.user().nickname()).isEqualTo("New Name");
        }
    }

    @Nested
    class ForgotPassword {

        @Test
        void returnsCodeOnSuccess() {
            ForgotPasswordRequest request = new ForgotPasswordRequest("jane@booknest.test");
            given(userService.issueResetCode("jane@booknest.test")).willReturn("123456");

            ForgotPasswordResponse result = controller.forgotPassword(request);

            assertThat(result.success()).isTrue();
            assertThat(result.devCode()).isEqualTo("123456");
        }

        @Test
        void returnsFailureWhenAccountUnknown() {
            ForgotPasswordRequest request = new ForgotPasswordRequest("ghost@booknest.test");
            given(userService.issueResetCode("ghost@booknest.test"))
                    .willThrow(new InvalidCredentialsException("Account not found"));

            ForgotPasswordResponse result = controller.forgotPassword(request);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("account_not_found");
        }
    }

    @Nested
    class ResetPassword {

        @Test
        void issuesCookiesOnSuccessfulReset() {
            ResetPasswordRequest request = new ResetPasswordRequest("jane@booknest.test", "123456", "newpass1");
            User user = user();
            given(userService.resetPassword("jane@booknest.test", "123456", "newpass1")).willReturn(user);
            given(jwtService.generateAccessToken(user)).willReturn("access");
            given(jwtService.generateRefreshToken(user)).willReturn("refresh");
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.resetPassword(request, response);

            assertThat(result.success()).isTrue();
            verify(cookieUtil).writeAuthCookies(response, "access", "refresh");
        }

        @Test
        void returnsFailureOnInvalidCode() {
            ResetPasswordRequest request = new ResetPasswordRequest("jane@booknest.test", "000000", "newpass1");
            given(userService.resetPassword("jane@booknest.test", "000000", "newpass1"))
                    .willThrow(new InvalidResetCodeException("Invalid or expired code"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.resetPassword(request, response);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("invalid_or_expired_code");
        }
    }

    @Nested
    class Logout {

        @Test
        void clearsCookiesWhenAuthenticated() {
            Authentication authentication = new TestingAuthenticationToken("jane@booknest.test", null);
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.logout(authentication, response);

            assertThat(result.success()).isTrue();
            verify(cookieUtil).clearAuthCookies(response);
        }

        @Test
        void clearsCookiesWhenNotAuthenticated() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            AuthResponse result = controller.logout(null, response);

            assertThat(result.success()).isTrue();
            verify(cookieUtil).clearAuthCookies(response);
        }
    }
}
