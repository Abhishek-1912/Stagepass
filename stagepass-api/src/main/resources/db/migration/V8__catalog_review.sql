-- Online events and review tracking for organizers and events.

alter table events
    add column is_online    boolean not null default false,
    add column online_url   text,
    add column submitted_at timestamptz,
    add column reviewed_by  uuid references users (id);
create index events_reviewed_by_idx on events (reviewed_by);

alter table organizers
    add column reviewed_by uuid references users (id),
    add column reviewed_at timestamptz,
    add column applied_at  timestamptz not null default now();
create index organizers_reviewed_by_idx on organizers (reviewed_by);
