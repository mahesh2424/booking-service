package com.mahesh.flashsale.booking.reservation;

import com.mahesh.flashsale.api.ReservationDtos;
import com.mahesh.flashsale.api.ReservationStatus;
import com.mahesh.flashsale.booking.redis.HoldRateLimiter;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReservationController {

    private final ReservationService reservationService;
    private final HoldRateLimiter holdRateLimiter;

    public ReservationController(ReservationService reservationService, HoldRateLimiter holdRateLimiter) {
        this.reservationService = reservationService;
        this.holdRateLimiter = holdRateLimiter;
    }

    @PostMapping("/events/{eventId}/reservations")
    public ResponseEntity<ReservationDtos.ReservationResponse> create(
            @PathVariable UUID eventId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReservationDtos.CreateReservationRequest request) {
        if (!holdRateLimiter.tryAcquire(request.customerEmail())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        }
        ReservationDtos.ReservationResponse response = reservationService.create(eventId, request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/reservations/" + response.id())).body(response);
    }

    @GetMapping("/reservations/{reservationId}")
    public ReservationDtos.ReservationResponse get(@PathVariable UUID reservationId) {
        return reservationService.get(reservationId);
    }

    @PostMapping("/reservations/{reservationId}/confirm")
    public ResponseEntity<ReservationDtos.ReservationResponse> confirm(@PathVariable UUID reservationId) {
        ReservationDtos.ReservationResponse response = reservationService.confirm(reservationId);
        if (response.status() == ReservationStatus.EXPIRED) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reservations/{reservationId}/cancel")
    public ReservationDtos.ReservationResponse cancel(@PathVariable UUID reservationId) {
        return reservationService.cancel(reservationId);
    }
}
