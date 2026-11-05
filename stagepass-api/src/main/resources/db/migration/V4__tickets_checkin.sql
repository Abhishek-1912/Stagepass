-- Tickets, check-ins (one per ticket, enforced by unique constraint), scan audit trail.

create table tickets (
    id               uuid primary key default gen_random_uuid(),
    booking_id       uuid not null references bookings (id),
    booking_item_id  uuid not null references booking_items (id),
    ticket_type_id   uuid not null references ticket_types (id),
    event_id         uuid not null references events (id),
    code             text not null unique,
    holder_name      text not null,
    status           text not null default 'VALID'
                         check (status in ('VALID', 'CHECKED_IN', 'CANCELLED', 'REFUNDED')),
    qr_nonce         text not null,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    version          bigint not null default 0
);
create index tickets_booking_idx on tickets (booking_id);
create index tickets_booking_item_idx on tickets (booking_item_id);
create index tickets_ticket_type_idx on tickets (ticket_type_id);
create index tickets_event_status_idx on tickets (event_id, status);
create trigger tickets_updated_at before update on tickets for each row execute function set_updated_at();

create table check_ins (
    id           uuid primary key default gen_random_uuid(),
    ticket_id    uuid not null unique references tickets (id),
    event_id     uuid not null references events (id),
    scanned_by   uuid not null references users (id),
    scanned_at   timestamptz not null default now(),
    device_info  text
);
create index check_ins_event_idx on check_ins (event_id);
create index check_ins_scanned_by_idx on check_ins (scanned_by);

create table scan_logs (
    id          uuid primary key default gen_random_uuid(),
    event_id    uuid not null references events (id),
    scanned_by  uuid not null references users (id),
    result      text not null check (result in ('VALID', 'ALREADY_USED', 'INVALID', 'WRONG_EVENT')),
    ticket_id   uuid references tickets (id),
    at          timestamptz not null default now()
);
create index scan_logs_event_at_idx on scan_logs (event_id, at);
create index scan_logs_scanned_by_idx on scan_logs (scanned_by);
create index scan_logs_ticket_idx on scan_logs (ticket_id);
