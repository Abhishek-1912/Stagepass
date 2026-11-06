package com.stagepass.identity.repository;

import com.stagepass.identity.domain.Organizer;
import com.stagepass.identity.domain.OrganizerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizerRepository extends JpaRepository<Organizer, UUID> {

    Optional<Organizer> findByUserId(UUID userId);

    Page<Organizer> findByStatus(OrganizerStatus status, Pageable pageable);
}
