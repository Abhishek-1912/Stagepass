package com.stagepass.catalog.repository;

import com.stagepass.catalog.domain.TicketType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    List<TicketType> findByEventIdOrderBySortOrderAscCreatedAtAsc(UUID eventId);

    long countByEventId(UUID eventId);
}
