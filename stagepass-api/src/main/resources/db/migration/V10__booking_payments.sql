-- Booking and payment details needed by Plan 4.

alter table bookings
    add column cancel_reason text;

-- One live checkout per user per event: starting a new one expires the old one first.
create unique index bookings_one_pending_per_user_event on bookings (user_id, event_id)
    where status = 'PENDING_PAYMENT';
create index bookings_user_event_status_idx on bookings (user_id, event_id, status);

alter table tickets
    add column refunded_at timestamptz;

-- Free bookings are refunded without a payment; their refund amount is 0.
alter table refunds alter column payment_id drop not null;
alter table refunds drop constraint refunds_amount_paise_check;
alter table refunds add constraint refunds_amount_paise_check check (amount_paise >= 0);
alter table refunds
    add column kind text not null default 'ORGANIZER'
        check (kind in ('CANCELLATION', 'RESCHEDULE', 'ATTENDEE_REQUEST', 'ORGANIZER', 'ADMIN', 'LATE_PAYMENT')),
    add column fee_paise bigint not null default 0,
    add column fee_gst_paise bigint not null default 0,
    add column commission_paise bigint not null default 0,
    add column ticket_ids uuid[] not null default '{}',
    add column failure_reason text;
