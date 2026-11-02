package com.mahesh.flashsale.booking.inventory;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketing.inventory.backend", havingValue = "jdbc")
public class InventoryJdbcDao implements InventoryOperations {

    private static final String RESERVE_SQL = """
            UPDATE event_inventory
            SET available_tickets = available_tickets - :quantity
            WHERE event_id = :eventId
              AND available_tickets >= :quantity
              AND sale_opens_at <= :now
              AND sale_closes_at >= :now
            """;

    private static final String RELEASE_SQL = """
            UPDATE event_inventory
            SET available_tickets = available_tickets + :quantity
            WHERE event_id = :eventId
              AND available_tickets + :quantity <= total_tickets
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryJdbcDao(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int reserveTickets(UUID eventId, int quantity, Instant now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("quantity", quantity)
                .addValue("now", now);
        return jdbcTemplate.update(RESERVE_SQL, params);
    }

    @Override
    public int releaseTickets(UUID eventId, int quantity) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("quantity", quantity);
        return jdbcTemplate.update(RELEASE_SQL, params);
    }
}
