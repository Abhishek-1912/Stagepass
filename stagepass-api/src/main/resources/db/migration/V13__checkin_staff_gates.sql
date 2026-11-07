-- Plan 7: door staff, gates, and gate-aware check-ins and scan logs.

create table event_staff (
    id           uuid primary key default gen_random_uuid(),
    organizer_id uuid not null references organizers (id),
    event_id     uuid references events (id),
    email        text not null,
    user_id      uuid references users (id),
    role         text not null default 'SCANNER' check (role in ('SCANNER')),
    status       text not null default 'ACTIVE' check (status in ('ACTIVE', 'REMOVED')),
    invited_by   uuid not null references users (id),
    created_at   timestamptz not null default now(),
    accepted_at  timestamptz,
    removed_at   timestamptz
);
-- One active invite per organizer, email and scope (a null event means all of the organizer's events).
create unique index event_staff_active_unique on event_staff
    (organizer_id, lower(email), coalesce(event_id, '00000000-0000-0000-0000-000000000000'::uuid))
    where status = 'ACTIVE';
create index event_staff_user_idx on event_staff (user_id) where status = 'ACTIVE';
create index event_staff_email_idx on event_staff (lower(email)) where status = 'ACTIVE';
create index event_staff_event_idx on event_staff (event_id);
create index event_staff_invited_by_idx on event_staff (invited_by);

create table gates (
    id          uuid primary key default gen_random_uuid(),
    event_id    uuid not null references events (id),
    name        text not null,
    sort_order  integer not null default 0,
    created_at  timestamptz not null default now()
);
create unique index gates_event_name_unique on gates (event_id, lower(name));

create table gate_ticket_types (
    gate_id        uuid not null references gates (id) on delete cascade,
    ticket_type_id uuid not null references ticket_types (id),
    primary key (gate_id, ticket_type_id)
);
create index gate_ticket_types_type_idx on gate_ticket_types (ticket_type_id);

alter table check_ins add column gate_id uuid references gates (id) on delete set null;
create index check_ins_gate_idx on check_ins (gate_id);

alter table scan_logs add column gate_id uuid references gates (id) on delete set null;
alter table scan_logs add column manual boolean not null default false;
alter table scan_logs drop constraint scan_logs_result_check;
alter table scan_logs add constraint scan_logs_result_check
    check (result in ('VALID', 'ALREADY_USED', 'INVALID', 'WRONG_EVENT', 'WRONG_GATE'));
create index scan_logs_gate_idx on scan_logs (gate_id);
