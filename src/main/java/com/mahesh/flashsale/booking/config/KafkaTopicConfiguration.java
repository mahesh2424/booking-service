package com.mahesh.flashsale.booking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "ticketing.kafka.enabled", havingValue = "true")
public class KafkaTopicConfiguration {

    @Bean
    NewTopic reservationEventsTopic(
            @Value("${ticketing.kafka.topic:booking.reservation-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic reservationEventsDlqTopic(
            @Value("${ticketing.kafka.dlq-topic:booking.reservation-events.dlq}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
