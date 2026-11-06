package com.stagepass.catalog.repository;

import com.stagepass.catalog.domain.Venue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    Optional<Venue> findByIdAndOrganizerId(UUID id, UUID organizerId);

    List<Venue> findByOrganizerIdOrderByNameAsc(UUID organizerId);
}
