-- Reviews, saved events, reports, notifications, email outbox.

create table reviews (
    id          uuid primary key default gen_random_uuid(),
    event_id    uuid not null references events (id) on delete cascade,
    user_id     uuid not null references users (id),
    rating      smallint not null check (rating between 1 and 5),
    comment     text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    unique (event_id, user_id)
);
create index reviews_user_idx on reviews (user_id);
create trigger reviews_updated_at before update on reviews for each row execute function set_updated_at();

create table saved_events (
    user_id     uuid not null references users (id) on delete cascade,
    event_id    uuid not null references events (id) on delete cascade,
    created_at  timestamptz not null default now(),
    primary key (user_id, event_id)
);
create index saved_events_event_idx on saved_events (event_id);

create table event_reports (
    id               uuid primary key default gen_random_uuid(),
    event_id         uuid not null references events (id) on delete cascade,
    reporter_id      uuid not null references users (id),
    reason           text not null,
    details          text,
    status           text not null default 'OPEN' check (status in ('OPEN', 'RESOLVED', 'DISMISSED')),
    resolution_note  text,
    resolved_by      uuid references users (id),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create index event_reports_event_idx on event_reports (event_id);
create index event_reports_reporter_idx on event_reports (reporter_id);
create index event_reports_resolved_by_idx on event_reports (resolved_by);
create index event_reports_status_idx on event_reports (status);
create trigger event_reports_updated_at before update on event_reports for each row execute function set_updated_at();

create table notifications (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null references users (id) on delete cascade,
    type        text not null,
    title       text not null,
    body        text,
    link        text,
    read_at     timestamptz,
    created_at  timestamptz not null default now()
);
create index notifications_user_read_idx on notifications (user_id, read_at);

create table email_outbox (
    id          uuid primary key default gen_random_uuid(),
    to_address  text not null,
    template    text not null,
    payload     jsonb not null,
    status      text not null default 'PENDING' check (status in ('PENDING', 'SENT', 'FAILED')),
    attempts    integer not null default 0,
    last_error  text,
    send_after  timestamptz not null default now(),
    sent_at     timestamptz,
    created_at  timestamptz not null default now()
);
create index email_outbox_pending_idx on email_outbox (send_after) where status = 'PENDING';
