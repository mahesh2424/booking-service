package com.mahesh.flashsale.booking.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "event_inventory")
public class EventInventory {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private int totalTickets;

    @Column(nullable = false)
    private int availableTickets;

    @Column(nullable = false)
    private Instant saleOpensAt;

    @Column(nullable = false)
    private Instant saleClosesAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPrice;

    protected EventInventory() {
    }

    public EventInventory(
            UUID eventId,
            String name,
            Instant saleOpensAt,
            Instant saleClosesAt,
            int totalTickets,
            BigDecimal ticketPrice) {
        this.eventId = eventId;
        this.name = name;
        this.saleOpensAt = saleOpensAt;
        this.saleClosesAt = saleClosesAt;
        this.totalTickets = totalTickets;
        this.availableTickets = totalTickets;
        this.ticketPrice = ticketPrice;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public int getTotalTickets() {
        return totalTickets;
    }

    public int getAvailableTickets() {
        return availableTickets;
    }

    public Instant getSaleOpensAt() {
        return saleOpensAt;
    }

    public Instant getSaleClosesAt() {
        return saleClosesAt;
    }

    public BigDecimal getTicketPrice() {
        return ticketPrice;
    }
}
