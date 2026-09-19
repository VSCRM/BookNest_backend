package com.booknest.auth.service;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import com.booknest.auth.exception.EmailAlreadyRegisteredException;
import com.booknest.auth.exception.InvalidCredentialsException;
import com.booknest.auth.exception.InvalidResetCodeException;
import com.booknest.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    private static User localUser(String email, String passwordHash) {
        return User.builder()
                .id(1L)
                .email(email)
                .name("Jane Reader")
                .passwordHash(passwordHash)
                .provider(AuthProvider.LOCAL)
                .role(Role.USER)
                .build();
    }

    @Nested
    class Register {

        @Test
        void savesNewUserWithEncodedPasswordAndLocalProvider() {
            given(userRepository.existsByEmail("jane@booknest.test")).willReturn(false);
            given(passwordEncoder.encode("s3cret!")).willReturn("hashed-password");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.register("jane@booknest.test", "s3cret!", "Jane Reader");

            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            User saved = captor.getValue();
            assertThat(saved.getEmail()).isEqualTo("jane@booknest.test");
            assertThat(saved.getPasswordHash()).isEqualTo("hashed-password");
            assertThat(saved.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(result).isSameAs(saved);
        }

        @Test
        void normalizesEmailCaseAndWhitespaceBeforeCheckingAndSaving() {
            given(userRepository.existsByEmail("jane@booknest.test")).willReturn(false);
            given(passwordEncoder.encode(anyString())).willReturn("hashed");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            userService.register("  Jane@BookNest.TEST ", "s3cret!", "Jane Reader");

            verify(userRepository).existsByEmail("jane@booknest.test");
            ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("jane@booknest.test");
        }

        @Test
        void throwsWhenEmailAlreadyRegistered() {
            given(userRepository.existsByEmail("jane@booknest.test")).willReturn(true);

            assertThatThrownBy(() -> userService.register("jane@booknest.test", "s3cret!", "Jane Reader"))
                    .isInstanceOf(EmailAlreadyRegisteredException.class);

            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    class Authenticate {

        @Test
        void returnsUserWhenPasswordMatches() {
            User user = localUser("jane@booknest.test", "hashed-password");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("s3cret!", "hashed-password")).willReturn(true);

            User result = userService.authenticate("jane@booknest.test", "s3cret!");

            assertThat(result).isSameAs(user);
        }

        @Test
        void normalizesEmailBeforeLookup() {
            User user = localUser("jane@booknest.test", "hashed-password");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), anyString())).willReturn(true);

            userService.authenticate(" JANE@BookNest.test", "s3cret!");

            verify(userRepository).findByEmail("jane@booknest.test");
        }

        @Test
        void throwsWhenAccountDoesNotExist() {
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.authenticate("ghost@booknest.test", "whatever"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void throwsWhenPasswordDoesNotMatch() {
            User user = localUser("jane@booknest.test", "hashed-password");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrong", "hashed-password")).willReturn(false);

            assertThatThrownBy(() -> userService.authenticate("jane@booknest.test", "wrong"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        void throwsWhenAccountIsGoogleOnly() {
            User googleUser = User.builder()
                    .email("jane@booknest.test")
                    .name("Jane Reader")
                    .provider(AuthProvider.GOOGLE)
                    .passwordHash(null)
                    .build();
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(googleUser));

            assertThatThrownBy(() -> userService.authenticate("jane@booknest.test", "whatever"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Google");
        }

        @Test
        void throwsWhenLocalAccountHasNullPasswordHash() {
            User user = localUser("jane@booknest.test", null);
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.authenticate("jane@booknest.test", "whatever"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    class FindOrCreateGoogleUser {

        @Test
        void returnsExistingUserWithoutSaving() {
            User existing = localUser("jane@booknest.test", "hashed");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(existing));

            User result = userService.findOrCreateGoogleUser("Jane@BookNest.test", "Jane Reader");

            assertThat(result).isSameAs(existing);
            verify(userRepository, never()).save(any());
        }

        @Test
        void createsNewGoogleUserWhenNoneExists() {
            given(userRepository.findByEmail("new@booknest.test")).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willReturn("hashed-random-password");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.findOrCreateGoogleUser("new@booknest.test", "New Reader");

            assertThat(result.getEmail()).isEqualTo("new@booknest.test");
            assertThat(result.getProvider()).isEqualTo(AuthProvider.GOOGLE);
            // No longer null: a Google-only account still gets a bcrypt hash of a
            // SecureRandom-generated placeholder password, so passwordHash is never
            // an unset/absent value on any account, Google or local.
            assertThat(result.getPasswordHash()).isEqualTo("hashed-random-password");
        }

        @Test
        void encodesADifferentRandomPasswordOnEachCall() {
            given(userRepository.findByEmail(anyString())).willReturn(Optional.empty());
            given(passwordEncoder.encode(anyString())).willAnswer(inv -> "hashed:" + inv.getArgument(0));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User first = userService.findOrCreateGoogleUser("a@booknest.test", "A");
            User second = userService.findOrCreateGoogleUser("b@booknest.test", "B");

            // Same raw password reused for every Google signup would make the
            // "random" placeholder credential predictable across accounts.
            assertThat(first.getPasswordHash()).isNotEqualTo(second.getPasswordHash());
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void updatesNameOnlyWhenPasswordBlank() {
            User user = localUser("jane@booknest.test", "old-hash");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.updateProfile("jane@booknest.test", "New Name", " ");

            assertThat(result.getName()).isEqualTo("New Name");
            assertThat(result.getPasswordHash()).isEqualTo("old-hash");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        void updatesPasswordAndForcesLocalProviderWhenPasswordProvided() {
            User user = User.builder()
                    .email("jane@booknest.test")
                    .name("Jane Reader")
                    .provider(AuthProvider.GOOGLE)
                    .build();
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(passwordEncoder.encode("newpass1")).willReturn("new-hash");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.updateProfile("jane@booknest.test", null, "newpass1");

            assertThat(result.getPasswordHash()).isEqualTo("new-hash");
            assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(result.getName()).isEqualTo("Jane Reader");
        }

        @Test
        void leavesNameAndPasswordUntouchedWhenBothBlank() {
            User user = localUser("jane@booknest.test", "old-hash");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.updateProfile("jane@booknest.test", null, null);

            assertThat(result.getName()).isEqualTo("Jane Reader");
            assertThat(result.getPasswordHash()).isEqualTo("old-hash");
        }

        @Test
        void throwsWhenAccountNotFound() {
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateProfile("ghost@booknest.test", "Name", null))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    class IssueResetCode {

        @Test
        void generatesSixDigitCodeAndPersistsExpiry() {
            User user = localUser("jane@booknest.test", "hash");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            String code = userService.issueResetCode("jane@booknest.test");

            assertThat(code).matches("\\d{6}");
            assertThat(user.getResetCode()).isEqualTo(code);
            assertThat(user.getResetCodeExpiresAt()).isAfter(Instant.now());
            verify(userRepository).save(user);
        }

        @Test
        void throwsWhenAccountNotFound() {
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.issueResetCode("ghost@booknest.test"))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Nested
    class ResetPassword {

        @Test
        void resetsPasswordAndClearsCodeWhenValid() {
            User user = localUser("jane@booknest.test", "old-hash");
            user.setResetCode("123456");
            user.setResetCodeExpiresAt(Instant.now().plusSeconds(60));
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));
            given(passwordEncoder.encode("newpass1")).willReturn("new-hash");
            given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

            User result = userService.resetPassword("jane@booknest.test", "123456", "newpass1");

            assertThat(result.getPasswordHash()).isEqualTo("new-hash");
            assertThat(result.getProvider()).isEqualTo(AuthProvider.LOCAL);
            assertThat(result.getResetCode()).isNull();
            assertThat(result.getResetCodeExpiresAt()).isNull();
        }

        @Test
        void throwsWhenAccountNotFound() {
            given(userRepository.findByEmail("ghost@booknest.test")).willReturn(Optional.empty());

            assertThatThrownBy(() -> userService.resetPassword("ghost@booknest.test", "123456", "newpass1"))
                    .isInstanceOf(InvalidResetCodeException.class);
        }

        @Test
        void throwsWhenNoCodeWasEverIssued() {
            User user = localUser("jane@booknest.test", "old-hash");
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPassword("jane@booknest.test", "123456", "newpass1"))
                    .isInstanceOf(InvalidResetCodeException.class);
        }

        @Test
        void throwsWhenCodeDoesNotMatch() {
            User user = localUser("jane@booknest.test", "old-hash");
            user.setResetCode("111111");
            user.setResetCodeExpiresAt(Instant.now().plusSeconds(60));
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPassword("jane@booknest.test", "222222", "newpass1"))
                    .isInstanceOf(InvalidResetCodeException.class);
        }

        @Test
        void throwsWhenCodeHasExpired() {
            User user = localUser("jane@booknest.test", "old-hash");
            user.setResetCode("123456");
            user.setResetCodeExpiresAt(Instant.now().minusSeconds(1));
            given(userRepository.findByEmail("jane@booknest.test")).willReturn(Optional.of(user));

            assertThatThrownBy(() -> userService.resetPassword("jane@booknest.test", "123456", "newpass1"))
                    .isInstanceOf(InvalidResetCodeException.class);

            verify(userRepository, times(1)).findByEmail(eq("jane@booknest.test"));
        }
    }
}
