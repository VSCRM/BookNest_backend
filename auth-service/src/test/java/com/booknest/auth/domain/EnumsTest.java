package com.booknest.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EnumsTest {

    @Test
    void authProviderHasLocalAndGoogle() {
        assertThat(AuthProvider.values()).containsExactly(AuthProvider.LOCAL, AuthProvider.GOOGLE);
        assertThat(AuthProvider.valueOf("LOCAL")).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    void roleHasUserAndAdmin() {
        assertThat(Role.values()).containsExactly(Role.USER, Role.ADMIN);
        assertThat(Role.valueOf("ADMIN")).isEqualTo(Role.ADMIN);
    }
}
