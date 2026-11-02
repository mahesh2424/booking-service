package com.mahesh.flashsale.booking.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "ticketing.kafka.enabled", havingValue = "true")
public class OutboxRelay {

    private final OutboxJdbcDao outboxJdbcDao;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxRelay(
            OutboxJdbcDao outboxJdbcDao,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${ticketing.kafka.topic:booking.reservation-events}") String topic) {
        this.outboxJdbcDao = outboxJdbcDao;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Scheduled(fixedDelayString = "${ticketing.outbox.poll-ms:1000}")
    @Transactional
    public void publishBatch() {
        for (OutboxJdbcDao.OutboxRecord record : outboxJdbcDao.lockUnpublished(50)) {
            try {
                kafkaTemplate.send(topic, record.partitionKey().toString(), record.payload()).get();
            } catch (Exception exception) {
                throw new IllegalStateException("Failed to publish outbox event " + record.id(), exception);
            }
            outboxJdbcDao.markPublished(record.id(), java.time.Instant.now());
        }
    }
}
