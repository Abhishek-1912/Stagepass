package com.stagepass.catalog.repository;

import com.stagepass.catalog.domain.Event;
import com.stagepass.catalog.domain.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findByIdAndOrganizerId(UUID id, UUID organizerId);

    Page<Event> findByOrganizerId(UUID organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatus(UUID organizerId, EventStatus status, Pageable pageable);

    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    @Query(value = "select exists (select 1 from bookings where event_id = :eventId)", nativeQuery = true)
    boolean hasBookings(@Param("eventId") UUID eventId);
}
