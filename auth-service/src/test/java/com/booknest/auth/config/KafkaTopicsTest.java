package com.booknest.auth.config;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaTopicsTest {

    @Test
    void exposesExpectedTopicNames() {
        assertThat(KafkaTopics.USER_REGISTERED).isEqualTo("auth.user-registered");
        assertThat(KafkaTopics.USER_LOGGED_IN).isEqualTo("auth.user-logged-in");
        assertThat(KafkaTopics.LOGIN_FAILED).isEqualTo("auth.login-failed");
    }

    @Test
    void isNotInstantiable() throws Exception {
        Constructor<KafkaTopics> constructor = KafkaTopics.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();

        constructor.setAccessible(true);
        // Invoking it directly for coverage purposes only — production code
        // never does this, the private modifier is what actually prevents it.
        constructor.newInstance();
    }
}
