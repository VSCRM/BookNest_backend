package com.booknest.auth.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaProducerConfigTest {

    private final KafkaProducerConfig config = new KafkaProducerConfig();

    @Test
    void producerFactoryIsConfiguredWithBootstrapServersAndSerializers() {
        ProducerFactory<String, Object> factory = config.producerFactory("kafka-host:9092");

        assertThat(factory).isInstanceOf(DefaultKafkaProducerFactory.class);
        var configProps = factory.getConfigurationProperties();
        assertThat(configProps.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG)).isEqualTo("kafka-host:9092");
        assertThat(configProps.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG)).isEqualTo(StringSerializer.class);
        assertThat(configProps.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG)).isEqualTo(JacksonJsonSerializer.class);
        assertThat(configProps.get(ProducerConfig.ACKS_CONFIG)).isEqualTo("all");
        assertThat(configProps.get(ProducerConfig.RETRIES_CONFIG)).isEqualTo(3);
    }

    /**
     * Regression test for the 60-second login hang: because this
     * ProducerFactory bean is defined manually, Spring Boot's auto-config
     * never applies spring.kafka.producer.properties.* from application.yml
     * to it, so max.block.ms silently fell back to the Kafka client's 60s
     * default whenever the broker was unreachable. It must be set here,
     * directly in code.
     */
    @Test
    void producerFactoryFailsFastInsteadOfBlockingFor60Seconds() {
        ProducerFactory<String, Object> factory = config.producerFactory("kafka-host:9092");
        var configProps = factory.getConfigurationProperties();

        assertThat(configProps.get(ProducerConfig.MAX_BLOCK_MS_CONFIG)).isEqualTo(3000);
        assertThat(configProps.get(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG)).isEqualTo(3000);
        assertThat(configProps.get(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG)).isEqualTo(5000);
    }

    @Test
    void kafkaTemplateWrapsGivenProducerFactory() {
        ProducerFactory<String, Object> factory = config.producerFactory("kafka-host:9092");

        KafkaTemplate<String, Object> template = config.kafkaTemplate(factory);

        assertThat(template.getProducerFactory()).isSameAs(factory);
    }
}
