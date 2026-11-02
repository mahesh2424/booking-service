package com.mahesh.flashsale.messaging;

import java.time.Instant;
import java.util.UUID;

public record ReservationDomainEvent(
        UUID messageId,
        String type,
        UUID eventId,
        UUID reservationId,
        String customerEmail,
        int quantity,
        int availableTickets,
        Instant occurredAt) {
}
