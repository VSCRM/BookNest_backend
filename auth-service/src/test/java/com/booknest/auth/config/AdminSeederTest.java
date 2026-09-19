package com.booknest.auth.config;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.Role;
import com.booknest.auth.domain.User;
import com.booknest.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AdminSeeder takes its env lookup as an injected {@code Function<String,String>},
 * so tests supply a fixed {@link Map} directly instead of mocking {@link System}
 * statically (which Mockito refuses to allow, since java.lang.System is loaded
 * too early in JVM bootstrap for that to be safe).
 */
@ExtendWith(MockitoExtension.class)
class AdminSeederTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminSeeder seederWithEnv(Map<String, String> env) {
        return new AdminSeeder(userRepository, passwordEncoder, env::get);
    }

    @Test
    void skipsSeedingWhenAdminAlreadyExists() throws Exception {
        given(userRepository.existsByEmail("admin@booknest.local")).willReturn(true);

        seederWithEnv(Map.of()).run();

        verify(userRepository, never()).save(any());
    }

    @Test
    void seedsDefaultAdminWithBuiltInPasswordAndWarnsWhenNothingIsConfigured() throws Exception {
        given(userRepository.existsByEmail("admin@booknest.local")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        seederWithEnv(Map.of()).run();

        verify(passwordEncoder).encode("admin12345");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedAdmin = captor.getValue();
        assertThat(savedAdmin.getEmail()).isEqualTo("admin@booknest.local");
        assertThat(savedAdmin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(savedAdmin.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(savedAdmin.getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test
    void seedsSingleLegacyAdminWithExplicitPasswordAndNoWarning() throws Exception {
        given(userRepository.existsByEmail("boss@booknest.test")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        seederWithEnv(Map.of(
                "ADMIN_EMAIL", "boss@booknest.test",
                "ADMIN_PASSWORD", "s3cret-real-pass")).run();

        verify(passwordEncoder).encode("s3cret-real-pass");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("boss@booknest.test");
    }

    @Test
    void seedsMultipleAdminsFromAdminAccountsList() throws Exception {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        seederWithEnv(Map.of(
                "ADMIN_ACCOUNTS", "first@booknest.test:pass-one,second@booknest.test:pass-two")).run();

        verify(userRepository, times(2)).save(any(User.class));
        verify(passwordEncoder).encode("pass-one");
        verify(passwordEncoder).encode("pass-two");
    }

    @Test
    void skipsMalformedAdminAccountsEntriesButSeedsTheValidOnes() throws Exception {
        given(userRepository.existsByEmail(any())).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        seederWithEnv(Map.of(
                "ADMIN_ACCOUNTS",
                ",no-colon-here,:no-email,onlyemail@booknest.test:,valid@booknest.test:valid-pass,")).run();

        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder).encode("valid-pass");
    }

    @Test
    void fallsBackToLegacyEmailWhenAdminAccountsHasNoValidEntries() throws Exception {
        given(userRepository.existsByEmail("fallback@booknest.test")).willReturn(false);
        given(passwordEncoder.encode(any())).willReturn("encoded-password");
        given(userRepository.save(any(User.class))).willAnswer(inv -> inv.getArgument(0));

        seederWithEnv(Map.of(
                "ADMIN_ACCOUNTS", "garbage,,:::",
                "ADMIN_EMAIL", "fallback@booknest.test",
                "ADMIN_PASSWORD", "fallback-pass")).run();

        verify(userRepository, times(1)).save(any(User.class));
        verify(passwordEncoder).encode("fallback-pass");
    }
}
