-- Organizer ledger, payouts, GST invoices, platform settings, audit log.

create table organizer_ledger_entries (
    id            uuid primary key default gen_random_uuid(),
    organizer_id  uuid not null references organizers (id),
    event_id      uuid references events (id),
    booking_id    uuid references bookings (id),
    type          text not null check (type in ('SALE', 'REFUND', 'COMMISSION', 'PAYOUT', 'ADJUSTMENT', 'TDS')),
    amount_paise  bigint not null,
    note          text,
    created_at    timestamptz not null default now()
);
create index ledger_organizer_created_idx on organizer_ledger_entries (organizer_id, created_at);
create index ledger_event_idx on organizer_ledger_entries (event_id);
create index ledger_booking_idx on organizer_ledger_entries (booking_id);

create table payouts (
    id            uuid primary key default gen_random_uuid(),
    organizer_id  uuid not null references organizers (id),
    amount_paise  bigint not null check (amount_paise > 0),
    reference     text not null,
    paid_at       timestamptz not null,
    recorded_by   uuid not null references users (id),
    note          text,
    created_at    timestamptz not null default now()
);
create index payouts_organizer_idx on payouts (organizer_id);
create index payouts_recorded_by_idx on payouts (recorded_by);

create table invoice_sequences (
    financial_year  text primary key,
    last_seq        integer not null default 0
);

create table invoices (
    id              uuid primary key default gen_random_uuid(),
    booking_id      uuid not null unique references bookings (id),
    number          text not null unique,
    financial_year  text not null,
    seq             integer not null,
    taxable_paise   bigint not null,
    cgst_paise      bigint not null default 0,
    sgst_paise      bigint not null default 0,
    igst_paise      bigint not null default 0,
    total_paise     bigint not null,
    issued_at       timestamptz not null default now(),
    unique (financial_year, seq)
);

create table platform_settings (
    key         text primary key,
    value       jsonb not null,
    updated_at  timestamptz not null default now(),
    updated_by  uuid references users (id)
);
create trigger platform_settings_updated_at before update on platform_settings
    for each row execute function set_updated_at();

create table audit_logs (
    id           uuid primary key default gen_random_uuid(),
    actor_id     uuid references users (id),
    action       text not null,
    entity_type  text not null,
    entity_id    text,
    before       jsonb,
    after        jsonb,
    ip           text,
    at           timestamptz not null default now()
);
create index audit_logs_entity_idx on audit_logs (entity_type, entity_id);
create index audit_logs_actor_idx on audit_logs (actor_id);
create index audit_logs_at_idx on audit_logs (at);
