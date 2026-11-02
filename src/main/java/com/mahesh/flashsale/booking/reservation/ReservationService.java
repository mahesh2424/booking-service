package com.mahesh.flashsale.booking.reservation;

import com.mahesh.flashsale.api.NotFoundException;
import com.mahesh.flashsale.api.ReservationDtos;
import com.mahesh.flashsale.api.ReservationStatus;
import com.mahesh.flashsale.booking.inventory.EventInventory;
import com.mahesh.flashsale.booking.inventory.EventInventoryRepository;
import com.mahesh.flashsale.booking.inventory.InventoryOperations;
import com.mahesh.flashsale.booking.outbox.ReservationEventPublisher;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ReservationService {

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    private final ReservationRepository reservationRepository;
    private final EventInventoryRepository inventoryRepository;
    private final InventoryOperations inventoryOperations;
    private final IdempotencyJdbcDao idempotencyJdbcDao;
    private final ReservationEventPublisher eventPublisher;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            EventInventoryRepository inventoryRepository,
            InventoryOperations inventoryOperations,
            IdempotencyJdbcDao idempotencyJdbcDao,
            ReservationEventPublisher eventPublisher,
            EntityManager entityManager,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.reservationRepository = reservationRepository;
        this.inventoryRepository = inventoryRepository;
        this.inventoryOperations = inventoryOperations;
        this.idempotencyJdbcDao = idempotencyJdbcDao;
        this.eventPublisher = eventPublisher;
        this.entityManager = entityManager;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    public ReservationDtos.ReservationResponse create(
            UUID eventId,
            ReservationDtos.CreateReservationRequest request,
            String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyJdbcDao.findReservationId(eventId, idempotencyKey)
                        .map(this::getReservationResponse)
                    .orElseGet(() -> createWithIdempotency(eventId, request, idempotencyKey.trim()));
        }
        return transactionTemplate.execute(status -> createHold(eventId, request, null));
    }

    private ReservationDtos.ReservationResponse createWithIdempotency(
            UUID eventId,
            ReservationDtos.CreateReservationRequest request,
            String idempotencyKey) {
        try {
            return transactionTemplate.execute(status -> createHold(eventId, request, idempotencyKey));
        } catch (DataIntegrityViolationException exception) {
            return idempotencyJdbcDao.findReservationId(eventId, idempotencyKey)
                    .map(this::getReservationResponse)
                    .orElseThrow(() -> exception);
        }
    }

    private ReservationDtos.ReservationResponse createHold(
            UUID eventId,
            ReservationDtos.CreateReservationRequest request,
            String idempotencyKey) {
        Instant now = clock.instant();
        int reserved = inventoryOperations.reserveTickets(eventId, request.quantity(), now);
        if (reserved == 0) {
            EventInventory event = inventoryRepository.findById(eventId)
                    .orElseThrow(() -> new NotFoundException("Event not found."));
            if (now.isBefore(event.getSaleOpensAt())) {
                throw new ReservationConflictException("Ticket sales have not opened.");
            }
            if (now.isAfter(event.getSaleClosesAt())) {
                throw new ReservationConflictException("Ticket sales have closed.");
            }
            throw new ReservationConflictException("Not enough tickets are available.");
        }

        EventInventory event = loadFreshInventory(eventId);
        Reservation reservation = new Reservation(
                event,
                request.customerEmail(),
                request.quantity(),
                event.getTicketPrice().multiply(java.math.BigDecimal.valueOf(request.quantity())),
                now,
                now.plus(HOLD_DURATION));
        reservationRepository.saveAndFlush(reservation);
        if (idempotencyKey != null) {
            idempotencyJdbcDao.save(eventId, idempotencyKey, reservation.getId(), now);
        }
        eventPublisher.publish("ReservationHeld", reservation, event.getAvailableTickets());
        return toResponse(reservation);
    }

    @Transactional(readOnly = true)
    public ReservationDtos.ReservationResponse get(UUID reservationId) {
        return toResponse(findReservation(reservationId));
    }

    @Transactional
    public ReservationDtos.ReservationResponse confirm(UUID reservationId) {
        Reservation reservation = findReservationForUpdate(reservationId);
        if (reservation.getStatus() == ReservationStatus.HELD
                && !clock.instant().isBefore(reservation.getExpiresAt())) {
            expire(reservation);
        } else if (reservation.getStatus() == ReservationStatus.HELD) {
            reservation.confirm();
            eventPublisher.publish(
                    "ReservationConfirmed",
                    reservation,
                    loadFreshInventory(reservation.getEvent().getEventId()).getAvailableTickets());
        } else if (reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new ReservationConflictException("This reservation has expired.");
        } else {
            throw new ReservationConflictException("Only an active hold can be confirmed.");
        }
        return toResponse(reservation);
    }

    @Transactional
    public ReservationDtos.ReservationResponse cancel(UUID reservationId) {
        Reservation reservation = findReservationForUpdate(reservationId);
        if (reservation.getStatus() == ReservationStatus.HELD) {
            reservation.cancel();
            release(reservation);
            eventPublisher.publish(
                    "ReservationCancelled",
                    reservation,
                    loadFreshInventory(reservation.getEvent().getEventId()).getAvailableTickets());
        } else {
            throw new ReservationConflictException("Only an active hold can be cancelled.");
        }
        return toResponse(reservation);
    }

    @Scheduled(fixedDelayString = "${ticketing.holds.expiry-scan-ms:30000}")
    @Transactional
    public void expireHolds() {
        List<UUID> expiredIds = reservationRepository.findIdsToExpire(
                ReservationStatus.HELD, clock.instant());
        for (UUID reservationId : expiredIds) {
            reservationRepository.findByIdForUpdate(reservationId)
                    .filter(reservation -> reservation.getStatus() == ReservationStatus.HELD
                            && !clock.instant().isBefore(reservation.getExpiresAt()))
                    .ifPresent(this::expire);
        }
    }

    private void expire(Reservation reservation) {
        reservation.expire();
        release(reservation);
        eventPublisher.publish(
                "ReservationExpired",
                reservation,
                loadFreshInventory(reservation.getEvent().getEventId()).getAvailableTickets());
    }

    private void release(Reservation reservation) {
        int released = inventoryOperations.releaseTickets(
                reservation.getEvent().getEventId(), reservation.getQuantity());
        if (released == 0) {
            throw new IllegalStateException("Reservation release would exceed event inventory.");
        }
    }

    private EventInventory loadFreshInventory(UUID eventId) {
        EventInventory event = inventoryRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found."));
        entityManager.refresh(event);
        return event;
    }

    private Reservation findReservation(UUID reservationId) {
        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found."));
    }

    private ReservationDtos.ReservationResponse getReservationResponse(UUID reservationId) {
        return reservationRepository.findByIdWithEvent(reservationId)
                .map(this::toResponse)
                .orElseThrow(() -> new NotFoundException("Reservation not found."));
    }

    private Reservation findReservationForUpdate(UUID reservationId) {
        return reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new NotFoundException("Reservation not found."));
    }

    private ReservationDtos.ReservationResponse toResponse(Reservation reservation) {
        return new ReservationDtos.ReservationResponse(
                reservation.getId(),
                reservation.getEvent().getEventId(),
                reservation.getEvent().getName(),
                reservation.getCustomerEmail(),
                reservation.getQuantity(),
                reservation.getTotalPrice(),
                reservation.getStatus(),
                reservation.getExpiresAt(),
                reservation.getCreatedAt());
    }

    public static class ReservationConflictException extends RuntimeException {
        public ReservationConflictException(String message) {
            super(message);
        }
    }
}
