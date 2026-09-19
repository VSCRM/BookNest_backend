package com.booknest.auth.repository;

import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class UserRepositoryTest {

    @org.springframework.beans.factory.annotation.Autowired
    private UserRepository userRepository;

    @Test
    void findByEmailReturnsSavedUser() {
        User user = User.builder()
                .email("jane@booknest.test")
                .name("Jane Reader")
                .passwordHash("hash")
                .provider(AuthProvider.LOCAL)
                .build();
        userRepository.save(user);

        assertThat(userRepository.findByEmail("jane@booknest.test")).isPresent();
        assertThat(userRepository.findByEmail("jane@booknest.test").get().getName()).isEqualTo("Jane Reader");
    }

    @Test
    void findByEmailReturnsEmptyWhenNoMatch() {
        assertThat(userRepository.findByEmail("ghost@booknest.test")).isEmpty();
    }

    @Test
    void existsByEmailReflectsSavedState() {
        assertThat(userRepository.existsByEmail("jane@booknest.test")).isFalse();

        userRepository.save(User.builder()
                .email("jane@booknest.test")
                .name("Jane Reader")
                .provider(AuthProvider.LOCAL)
                .build());

        assertThat(userRepository.existsByEmail("jane@booknest.test")).isTrue();
    }

    @Test
    void createdAtIsStampedOnPersist() {
        User user = User.builder()
                .email("jane@booknest.test")
                .name("Jane Reader")
                .provider(AuthProvider.LOCAL)
                .build();

        User saved = userRepository.save(user);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getId()).isNotNull();
    }
}
