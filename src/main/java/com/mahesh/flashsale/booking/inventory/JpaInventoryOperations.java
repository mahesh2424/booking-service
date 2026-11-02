package com.mahesh.flashsale.booking.inventory;

import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ticketing.inventory.backend", havingValue = "jpa", matchIfMissing = true)
public class JpaInventoryOperations implements InventoryOperations {

    private final EventInventoryRepository inventoryRepository;

    public JpaInventoryOperations(EventInventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public int reserveTickets(UUID eventId, int quantity, Instant now) {
        return inventoryRepository.reserveTickets(eventId, quantity, now);
    }

    @Override
    public int releaseTickets(UUID eventId, int quantity) {
        return inventoryRepository.releaseTickets(eventId, quantity);
    }
}
