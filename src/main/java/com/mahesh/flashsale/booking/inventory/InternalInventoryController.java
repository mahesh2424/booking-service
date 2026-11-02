package com.mahesh.flashsale.booking.inventory;

import com.mahesh.flashsale.api.InventoryDtos;
import com.mahesh.flashsale.api.NotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/inventory")
public class InternalInventoryController {

    private final EventInventoryRepository inventoryRepository;

    public InternalInventoryController(EventInventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public InventoryDtos.InventoryAvailability seed(@RequestBody InventoryDtos.SeedInventoryRequest request) {
        EventInventory inventory = inventoryRepository.findById(request.eventId())
                .orElseGet(() -> inventoryRepository.save(new EventInventory(
                        request.eventId(),
                        request.name(),
                        request.saleOpensAt(),
                        request.saleClosesAt(),
                        request.totalTickets(),
                        request.ticketPrice())));
        return new InventoryDtos.InventoryAvailability(
                inventory.getEventId(), inventory.getAvailableTickets(), inventory.getTotalTickets());
    }

    @GetMapping
    public List<InventoryDtos.InventoryAvailability> list() {
        return inventoryRepository.findAll().stream()
                .map(inventory -> new InventoryDtos.InventoryAvailability(
                        inventory.getEventId(), inventory.getAvailableTickets(), inventory.getTotalTickets()))
                .toList();
    }

    @GetMapping("/{eventId}")
    public InventoryDtos.InventoryAvailability get(@PathVariable UUID eventId) {
        EventInventory inventory = inventoryRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event inventory not found."));
        return new InventoryDtos.InventoryAvailability(
                inventory.getEventId(), inventory.getAvailableTickets(), inventory.getTotalTickets());
    }
}
