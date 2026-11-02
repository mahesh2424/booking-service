package com.mahesh.flashsale.booking.reservation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyJdbcDao {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public IdempotencyJdbcDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findReservationId(UUID eventId, String idempotencyKey) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("idempotencyKey", idempotencyKey);
        try {
            UUID reservationId = jdbcTemplate.queryForObject(
                    """
                    SELECT reservation_id
                    FROM reservation_idempotency_keys
                    WHERE event_id = :eventId AND idempotency_key = :idempotencyKey
                    """,
                    params,
                    UUID.class);
            return Optional.ofNullable(reservationId);
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    public void save(UUID eventId, String idempotencyKey, UUID reservationId, Instant createdAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("reservationId", reservationId)
                .addValue("createdAt", createdAt);
        jdbcTemplate.update(
                """
                INSERT INTO reservation_idempotency_keys
                    (event_id, idempotency_key, reservation_id, created_at)
                VALUES (:eventId, :idempotencyKey, :reservationId, :createdAt)
                """,
                params);
    }
}
