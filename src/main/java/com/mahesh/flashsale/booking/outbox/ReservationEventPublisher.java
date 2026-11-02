package com.mahesh.flashsale.booking.outbox;

import com.mahesh.flashsale.booking.reservation.Reservation;
import com.mahesh.flashsale.messaging.ReservationDomainEvent;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReservationEventPublisher {

    private final OutboxJdbcDao outboxJdbcDao;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReservationEventPublisher(OutboxJdbcDao outboxJdbcDao, ObjectMapper objectMapper, Clock clock) {
        this.outboxJdbcDao = outboxJdbcDao;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(String type, Reservation reservation, int availableTickets) {
        UUID messageId = UUID.randomUUID();
        ReservationDomainEvent event = new ReservationDomainEvent(
                messageId,
                type,
                reservation.getEvent().getEventId(),
                reservation.getId(),
                reservation.getCustomerEmail(),
                reservation.getQuantity(),
                availableTickets,
                clock.instant());
        try {
            outboxJdbcDao.append(
                    messageId,
                    type,
                    reservation.getId(),
                    reservation.getEvent().getEventId(),
                    objectMapper.writeValueAsString(event),
                    event.occurredAt());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Could not serialize reservation domain event.", exception);
        }
    }
}
