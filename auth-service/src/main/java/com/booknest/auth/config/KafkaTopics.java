package com.booknest.auth.config;

/** Central registry of Kafka topic names this service produces to. */
public final class KafkaTopics {

    public static final String USER_REGISTERED = "auth.user-registered";
    public static final String USER_LOGGED_IN = "auth.user-logged-in";
    public static final String LOGIN_FAILED = "auth.login-failed";

    private KafkaTopics() {
    }
}
