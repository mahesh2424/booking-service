package com.mahesh.flashsale.booking.outbox;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class OutboxJdbcDao {

    public record OutboxRecord(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID partitionKey,
            String payload) {
    }

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String lockClause;

    public OutboxJdbcDao(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${ticketing.outbox.lock-clause:FOR UPDATE}") String lockClause) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockClause = lockClause;
    }

    public void append(
            UUID id,
            String eventType,
            UUID aggregateId,
            UUID partitionKey,
            String payload,
            Instant createdAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("eventType", eventType)
                .addValue("aggregateId", aggregateId)
                .addValue("partitionKey", partitionKey)
                .addValue("payload", payload)
                .addValue("createdAt", createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events
                    (id, event_type, aggregate_id, partition_key, payload, created_at)
                VALUES (:id, :eventType, :aggregateId, :partitionKey, :payload, :createdAt)
                """,
                params);
    }

    public List<OutboxRecord> lockUnpublished(int batchSize) {
        String sql = """
                SELECT id, event_type, aggregate_id, partition_key, payload
                FROM outbox_events
                WHERE published_at IS NULL
                ORDER BY created_at
                LIMIT :batchSize
                """ + " " + lockClause;
        return jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("batchSize", batchSize),
                (rs, rowNum) -> new OutboxRecord(
                        rs.getObject("id", UUID.class),
                        rs.getString("event_type"),
                        rs.getObject("aggregate_id", UUID.class),
                        rs.getObject("partition_key", UUID.class),
                        rs.getString("payload")));
    }

    public void markPublished(UUID id, Instant publishedAt) {
        jdbcTemplate.update(
                "UPDATE outbox_events SET published_at = :publishedAt WHERE id = :id",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("publishedAt", Timestamp.from(publishedAt)));
    }
}
