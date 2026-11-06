package com.stagepass.booking.repository;

import com.stagepass.booking.domain.Booking;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    Optional<Booking> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<Booking> findByIdempotencyKey(String idempotencyKey);

    boolean existsByCode(String code);

    /** Row lock so concurrent confirmations, expiries and refunds of one booking run one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findForUpdate(@Param("id") UUID id);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Booking> findByEventIdAndStatusIn(UUID eventId, Collection<com.stagepass.booking.domain.BookingStatus> statuses);

    Optional<Booking> findFirstByUserIdAndEventIdAndStatus(UUID userId, UUID eventId,
            com.stagepass.booking.domain.BookingStatus status);
}
