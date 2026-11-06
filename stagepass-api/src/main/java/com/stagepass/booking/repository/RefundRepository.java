package com.stagepass.booking.repository;

import com.stagepass.booking.domain.Refund;
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

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByRazorpayRefundId(String razorpayRefundId);

    List<Refund> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    @Query("select r from Refund r where r.bookingId in (select b.id from Booking b where b.eventId = :eventId) order by r.createdAt desc")
    List<Refund> findByEventId(@Param("eventId") UUID eventId);
}
