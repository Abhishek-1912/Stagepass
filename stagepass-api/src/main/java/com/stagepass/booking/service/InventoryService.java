package com.stagepass.booking.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ticket counters, changed only by single conditional updates so concurrent buyers can never push
 * {@code sold + held} past {@code total}. Call inside the caller's transaction.
 */
@Service
public class InventoryService {

    private final JdbcTemplate jdbc;

    public InventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Holds {@code qty} tickets if that many are still available; false when they are not. */
    public boolean hold(UUID typeId, int qty) {
        return jdbc.update("""
                update ticket_inventory set held = held + ?
                where ticket_type_id = ? and total - sold - held >= ?
                """, qty, typeId, qty) == 1;
    }

    /** Gives back a hold (expiry, replacement, gateway failure). */
    public void release(UUID typeId, int qty) {
        jdbc.update("update ticket_inventory set held = greatest(held - ?, 0) where ticket_type_id = ?", qty, typeId);
    }

    /** Turns held tickets into sold ones at confirmation. */
    public void commit(UUID typeId, int qty) {
        int n = jdbc.update("""
                update ticket_inventory set held = held - ?, sold = sold + ?
                where ticket_type_id = ? and held >= ?
                """, qty, qty, typeId, qty);
        if (n != 1) {
            throw new IllegalStateException("No hold of " + qty + " to commit for ticket type " + typeId);
        }
    }

    /** Sells without a prior hold (a late payment whose hold expired); false when not enough are left. */
    public boolean sellWithoutHold(UUID typeId, int qty) {
        return jdbc.update("""
                update ticket_inventory set sold = sold + ?
                where ticket_type_id = ? and total - sold - held >= ?
                """, qty, typeId, qty) == 1;
    }

    /** Puts refunded tickets back on sale. */
    public void returnSold(UUID typeId, int qty) {
        jdbc.update("update ticket_inventory set sold = greatest(sold - ?, 0) where ticket_type_id = ?", qty, typeId);
    }
}
