package com.mahesh.flashsale.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record SeedInventoryRequest(
            UUID eventId,
            String name,
            Instant saleOpensAt,
            Instant saleClosesAt,
            int totalTickets,
            BigDecimal ticketPrice) {
    }

    public record InventoryAvailability(UUID eventId, int availableTickets, int totalTickets) {
    }
}
