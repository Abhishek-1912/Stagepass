package com.stagepass.catalog.repository;

import com.stagepass.catalog.domain.TicketInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TicketInventoryRepository extends JpaRepository<TicketInventory, UUID> {

    /** Sets a new total only if it still covers sold + held; returns 0 when it would not (race-safe). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update ticket_inventory set total = :total
            where ticket_type_id = :ticketTypeId and sold + held <= :total
            """, nativeQuery = true)
    int resize(@Param("ticketTypeId") UUID ticketTypeId, @Param("total") int total);
}
