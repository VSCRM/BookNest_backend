package com.booknest.auth.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void builderDefaultsRoleToUser() {
        User user = User.builder()
                .email("jane@booknest.test")
                .name("Jane Reader")
                .provider(AuthProvider.LOCAL)
                .build();

        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    @Test
    void builderAllowsExplicitRole() {
        User user = User.builder()
                .email("admin@booknest.test")
                .name("Admin")
                .provider(AuthProvider.LOCAL)
                .role(Role.ADMIN)
                .build();

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void gettersAndSettersRoundTrip() {
        User user = new User();
        Instant expiry = Instant.now().plusSeconds(900);

        user.setId(7L);
        user.setEmail("jane@booknest.test");
        user.setName("Jane Reader");
        user.setPasswordHash("hash");
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.USER);
        user.setResetCode("123456");
        user.setResetCodeExpiresAt(expiry);

        assertThat(user.getId()).isEqualTo(7L);
        assertThat(user.getEmail()).isEqualTo("jane@booknest.test");
        assertThat(user.getName()).isEqualTo("Jane Reader");
        assertThat(user.getPasswordHash()).isEqualTo("hash");
        assertThat(user.getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getResetCode()).isEqualTo("123456");
        assertThat(user.getResetCodeExpiresAt()).isEqualTo(expiry);
    }

    @Test
    void onCreateStampsCreatedAt() {
        User user = new User();
        assertThat(user.getCreatedAt()).isNull();

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        Instant now = Instant.now();
        User user = new User(1L, "jane@booknest.test", "Jane Reader", "hash",
                AuthProvider.LOCAL, Role.USER, "123456", now.plusSeconds(60), now);

        assertThat(user.getId()).isEqualTo(1L);
        assertThat(user.getCreatedAt()).isEqualTo(now);
    }
}
