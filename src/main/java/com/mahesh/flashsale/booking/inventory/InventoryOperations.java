package com.mahesh.flashsale.booking.inventory;

import java.time.Instant;
import java.util.UUID;

public interface InventoryOperations {

    int reserveTickets(UUID eventId, int quantity, Instant now);

    int releaseTickets(UUID eventId, int quantity);
}
