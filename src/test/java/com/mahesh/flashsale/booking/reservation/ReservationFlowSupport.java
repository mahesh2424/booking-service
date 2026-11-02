package com.mahesh.flashsale.booking.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mahesh.flashsale.api.ReservationDtos;
import com.mahesh.flashsale.api.ReservationStatus;
import com.mahesh.flashsale.booking.inventory.EventInventory;
import com.mahesh.flashsale.booking.inventory.EventInventoryRepository;
import com.mahesh.flashsale.booking.outbox.OutboxJdbcDao;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(ReservationFlowSupport.TestClockConfiguration.class)
abstract class ReservationFlowSupport {

    static final Instant TEST_NOW = Instant.parse("2026-01-01T12:00:00Z");

    @Autowired
    ReservationService reservationService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    EventInventoryRepository inventoryRepository;

    @Autowired
    OutboxJdbcDao outboxJdbcDao;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    MutableClock clock;

    @BeforeEach
    void clearEvents() {
        jdbcTemplate.update("DELETE FROM reservation_idempotency_keys");
        jdbcTemplate.update("DELETE FROM outbox_events");
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        clock.setInstant(TEST_NOW);
    }

    @Test
    void holdsAndConfirmsTicketsAndUpdatesInventory() {
        EventInventory event = createEvent(5);

        ReservationDtos.ReservationResponse hold = reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("fan@example.com", 2),
                null);
        ReservationDtos.ReservationResponse confirmed = reservationService.confirm(hold.id());

        assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(confirmed.totalPrice()).isEqualByComparingTo("179.00");
        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isEqualTo(3);
        assertThat(unpublishedEventTypes()).contains("ReservationHeld", "ReservationConfirmed");
    }

    @Test
    void cancellingHoldReleasesInventoryOnlyOnce() {
        EventInventory event = createEvent(4);
        ReservationDtos.ReservationResponse hold = reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("fan@example.com", 3),
                null);

        reservationService.cancel(hold.id());

        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isEqualTo(4);
        assertThatThrownBy(() -> reservationService.cancel(hold.id()))
                .isInstanceOf(ReservationService.ReservationConflictException.class);
        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isEqualTo(4);
    }

    @Test
    void preventsReservationsBeyondAvailableInventory() {
        EventInventory event = createEvent(2);
        reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("first@example.com", 2),
                null);

        assertThatThrownBy(() -> reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("second@example.com", 1),
                null))
                .isInstanceOf(ReservationService.ReservationConflictException.class)
                .hasMessageContaining("Not enough tickets");
        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isZero();
    }

    @Test
    void concurrentFlashSaleRequestsCannotOversell() throws Exception {
        EventInventory event = createEvent(3);
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();

        try {
            IntStream.range(0, 10).forEach(index -> attempts.add(executor.submit(() -> {
                start.await();
                try {
                    reservationService.create(
                            event.getEventId(),
                            new ReservationDtos.CreateReservationRequest("fan" + index + "@example.com", 1),
                            null);
                    return true;
                } catch (ReservationService.ReservationConflictException exception) {
                    return false;
                }
            })));
            start.countDown();

            long successfulHolds = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get()) {
                    successfulHolds++;
                }
            }

            assertThat(successfulHolds).isEqualTo(3);
            assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void expiresOverdueHoldsAndReleasesInventory() {
        EventInventory event = createEvent(3);
        ReservationDtos.ReservationResponse hold = reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("fan@example.com", 2),
                null);

        clock.setInstant(TEST_NOW.plus(11, ChronoUnit.MINUTES));
        reservationService.expireHolds();

        assertThat(reservationService.get(hold.id()).status()).isEqualTo(ReservationStatus.EXPIRED);
        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isEqualTo(3);
        assertThat(unpublishedEventTypes()).contains("ReservationExpired");
    }

    @Test
    void retriesWithSameIdempotencyKeyDoNotCreateDuplicateHolds() {
        EventInventory event = createEvent(5);
        String key = "hold-" + UUID.randomUUID();

        ReservationDtos.ReservationResponse first = reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("fan@example.com", 2),
                key);
        ReservationDtos.ReservationResponse retry = reservationService.create(
                event.getEventId(),
                new ReservationDtos.CreateReservationRequest("fan@example.com", 2),
                key);

        assertThat(retry.id()).isEqualTo(first.id());
        assertThat(inventoryRepository.findById(event.getEventId()).orElseThrow().getAvailableTickets()).isEqualTo(3);
        assertThat(reservationRepository.count()).isEqualTo(1);
    }

    EventInventory createEvent(int inventory) {
        Instant now = clock.instant();
        EventInventory event = new EventInventory(
                UUID.randomUUID(),
                "Test concert",
                now.minus(1, ChronoUnit.MINUTES),
                now.plus(12, ChronoUnit.HOURS),
                inventory,
                new BigDecimal("89.50"));
        return inventoryRepository.saveAndFlush(event);
    }

    List<String> unpublishedEventTypes() {
        return outboxJdbcDao.lockUnpublished(20).stream()
                .map(OutboxJdbcDao.OutboxRecord::eventType)
                .toList();
    }

    @TestConfiguration
    static class TestClockConfiguration {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(TEST_NOW);
        }
    }

    static class MutableClock extends Clock {

        private final AtomicReference<Instant> instant;

        MutableClock(Instant instant) {
            this.instant = new AtomicReference<>(instant);
        }

        void setInstant(Instant value) {
            instant.set(value);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
