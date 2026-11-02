package com.mahesh.flashsale.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record CreateReservationRequest(
            @NotBlank @Email String customerEmail,
            @Min(1) @Max(8) int quantity) {
    }

    public record ReservationResponse(
            UUID id,
            UUID eventId,
            String eventName,
            String customerEmail,
            int quantity,
            BigDecimal totalPrice,
            ReservationStatus status,
            Instant expiresAt,
            Instant createdAt) {
    }
}
