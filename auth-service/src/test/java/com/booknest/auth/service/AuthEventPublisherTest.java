package com.booknest.auth.service;

import com.booknest.auth.config.KafkaTopics;
import com.booknest.auth.domain.AuthProvider;
import com.booknest.auth.domain.User;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private AuthEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new AuthEventPublisher(kafkaTemplate);
    }

    private static User user() {
        return User.builder()
                .email("jane@booknest.test")
                .name("Jane Reader")
                .provider(AuthProvider.LOCAL)
                .build();
    }

    @Test
    void publishUserRegisteredSendsToTheRegisteredTopicWithEmailAsKey() {
        given(kafkaTemplate.send(eq(KafkaTopics.USER_REGISTERED), eq("jane@booknest.test"), any()))
                .willReturn(new CompletableFuture<>());

        publisher.publishUserRegistered(user());

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_REGISTERED), eq("jane@booknest.test"), any());
    }

    @Test
    void publishUserLoggedInSendsToTheLoggedInTopic() {
        given(kafkaTemplate.send(eq(KafkaTopics.USER_LOGGED_IN), eq("jane@booknest.test"), any()))
                .willReturn(new CompletableFuture<>());

        publisher.publishUserLoggedIn(user());

        verify(kafkaTemplate).send(eq(KafkaTopics.USER_LOGGED_IN), eq("jane@booknest.test"), any());
    }

    @Test
    void publishLoginFailedSendsToTheLoginFailedTopic() {
        given(kafkaTemplate.send(eq(KafkaTopics.LOGIN_FAILED), eq("jane@booknest.test"), any()))
                .willReturn(new CompletableFuture<>());

        publisher.publishLoginFailed("jane@booknest.test", "invalid_credentials");

        verify(kafkaTemplate).send(eq(KafkaTopics.LOGIN_FAILED), eq("jane@booknest.test"), any());
    }

    @Test
    void successfulSendCompletionIsHandledWithoutThrowing() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        assertThatCode(() -> publisher.publishUserRegistered(user())).doesNotThrowAnyException();

        RecordMetadata metadata = mock(RecordMetadata.class);
        given(metadata.offset()).willReturn(42L);
        @SuppressWarnings("unchecked")
        SendResult<String, Object> sendResult = mock(SendResult.class);
        given(sendResult.getRecordMetadata()).willReturn(metadata);

        assertThatCode(() -> future.complete(sendResult)).doesNotThrowAnyException();
    }

    @Test
    void failedSendCompletionIsLoggedWithoutThrowing() {
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        given(kafkaTemplate.send(any(), any(), any())).willReturn(future);

        publisher.publishLoginFailed("jane@booknest.test", "invalid_credentials");

        assertThatCode(() -> future.completeExceptionally(new RuntimeException("broker unreachable")))
                .doesNotThrowAnyException();
    }

    /**
     * KafkaTemplate#send() looks purely async but can throw synchronously
     * (e.g. a KafkaException wrapping a TimeoutException when partition
     * metadata can't be fetched within max.block.ms). That must be caught
     * so a login/register request never turns into a 500.
     */
    @Test
    void synchronousSendFailureIsCaughtAndLoggedWithoutThrowing() {
        given(kafkaTemplate.send(any(), any(), any()))
                .willThrow(new org.apache.kafka.common.KafkaException("partition metadata unavailable"));

        assertThatCode(() -> publisher.publishUserRegistered(user())).doesNotThrowAnyException();
    }
}
