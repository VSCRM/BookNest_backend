package com.booknest.auth.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import java.util.Map;

/**
 * Producer-only Kafka configuration: this service emits audit/domain
 * events (registration, login, login failure) for downstream consumers
 * (analytics, fraud detection, notification services) but does not
 * currently consume anything itself.
 */
@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers
    ) {
        // NOTE: because this bean is defined manually, Spring Boot's Kafka
        // auto-configuration (and therefore spring.kafka.producer.properties.*
        // in application.yml) is never applied to it. Any producer-level
        // timeout/tuning must be set here directly, or it silently falls back
        // to the Kafka client defaults (e.g. max.block.ms = 60000), which is
        // what was causing login requests to hang for a full minute whenever
        // the broker was unreachable.
        Map<String, Object> configProps = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JacksonJsonSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.RETRIES_CONFIG, 3,
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 3000,
                ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000,
                ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5000
        );
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
