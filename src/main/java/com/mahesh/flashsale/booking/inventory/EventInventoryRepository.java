package com.mahesh.flashsale.booking.inventory;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventInventoryRepository extends JpaRepository<EventInventory, UUID> {

    @Modifying
    @Query("""
            update EventInventory inventory
            set inventory.availableTickets = inventory.availableTickets - :quantity
            where inventory.eventId = :eventId
              and inventory.availableTickets >= :quantity
              and inventory.saleOpensAt <= :now
              and inventory.saleClosesAt >= :now
            """)
    int reserveTickets(
            @Param("eventId") UUID eventId,
            @Param("quantity") int quantity,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update EventInventory inventory
            set inventory.availableTickets = inventory.availableTickets + :quantity
            where inventory.eventId = :eventId
              and inventory.availableTickets + :quantity <= inventory.totalTickets
            """)
    int releaseTickets(@Param("eventId") UUID eventId, @Param("quantity") int quantity);
}
