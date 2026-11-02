package com.mahesh.flashsale.booking.reservation;

import com.mahesh.flashsale.api.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reservation from Reservation reservation where reservation.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select reservation from Reservation reservation
            join fetch reservation.event
            where reservation.id = :id
            """)
    Optional<Reservation> findByIdWithEvent(@Param("id") UUID id);

    @Query("""
            select reservation.id from Reservation reservation
            where reservation.status = :status and reservation.expiresAt <= :now
            """)
    List<UUID> findIdsToExpire(
            @Param("status") ReservationStatus status,
            @Param("now") Instant now);
}
