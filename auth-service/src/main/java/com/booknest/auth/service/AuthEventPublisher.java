package com.booknest.auth.service;

import com.booknest.auth.config.KafkaTopics;
import com.booknest.auth.domain.User;
import com.booknest.auth.dto.LoginFailedEvent;
import com.booknest.auth.dto.UserLoggedInEvent;
import com.booknest.auth.dto.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publishes security-relevant domain events to Kafka for downstream
 * consumers (audit trail, brute-force/fraud detection, notifications).
 * Publishing failures are logged but never allowed to fail the request
 * that triggered them — auth must keep working even if the broker is down.
 */
@Component
@RequiredArgsConstructor
public class AuthEventPublisher {

    private static final Logger log = LogManager.getLogger(AuthEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(User user) {
        var event = new UserRegisteredEvent(user.getEmail(), user.getName(), user.getProvider().name(), Instant.now());
        send(KafkaTopics.USER_REGISTERED, user.getEmail(), event);
    }

    public void publishUserLoggedIn(User user) {
        var event = new UserLoggedInEvent(user.getEmail(), user.getProvider().name(), Instant.now());
        send(KafkaTopics.USER_LOGGED_IN, user.getEmail(), event);
    }

    public void publishLoginFailed(String email, String reason) {
        var event = new LoginFailedEvent(email, reason, Instant.now());
        send(KafkaTopics.LOGIN_FAILED, email, event);
    }

    private void send(String topic, String key, Object event) {
        // KafkaTemplate#send() looks purely async, but the call itself can
        // throw synchronously (e.g. KafkaException wrapping a TimeoutException
        // when partition metadata can't be fetched within max.block.ms). That
        // exception would otherwise propagate straight out of AuthController
        // and turn a login/register request into a 500 — exactly what the
        // class-level javadoc says must never happen. The .whenComplete()
        // below only covers the *async* failure path (send accepted, then
        // broker never acked); this try/catch covers the synchronous one.
        try {
            kafkaTemplate.send(topic, key, event).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish event to topic={} key={}: {}", topic, key, ex.getMessage());
                } else {
                    log.debug("Published event to topic={} key={} offset={}",
                            topic, key, result.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            log.warn("Failed to publish event to topic={} key={}: {}", topic, key, ex.getMessage());
        }
    }
}
