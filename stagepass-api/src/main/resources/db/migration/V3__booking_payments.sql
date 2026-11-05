-- Bookings, payments, refunds, disputes, webhook deduplication.

create table bookings (
    id                uuid primary key default gen_random_uuid(),
    code              text not null unique,
    user_id           uuid not null references users (id),
    event_id          uuid not null references events (id),
    status            text not null default 'PENDING_PAYMENT'
                          check (status in ('PENDING_PAYMENT', 'CONFIRMED', 'EXPIRED', 'CANCELLED',
                                            'REFUNDED', 'PARTIALLY_REFUNDED')),
    subtotal_paise    bigint not null check (subtotal_paise >= 0),
    fee_paise         bigint not null default 0 check (fee_paise >= 0),
    fee_gst_paise     bigint not null default 0 check (fee_gst_paise >= 0),
    total_paise       bigint not null check (total_paise >= 0),
    commission_paise  bigint not null default 0 check (commission_paise >= 0),
    hold_expires_at   timestamptz,
    confirmed_at      timestamptz,
    attendee_name     text not null,
    attendee_email    text not null,
    attendee_phone    text,
    buyer_state       text,
    idempotency_key   text unique,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    version           bigint not null default 0,
    check (total_paise = subtotal_paise + fee_paise + fee_gst_paise)
);
create index bookings_user_idx on bookings (user_id);
create index bookings_event_status_idx on bookings (event_id, status);
create index bookings_pending_expiry_idx on bookings (hold_expires_at) where status = 'PENDING_PAYMENT';
create trigger bookings_updated_at before update on bookings for each row execute function set_updated_at();

create table booking_items (
    id                uuid primary key default gen_random_uuid(),
    booking_id        uuid not null references bookings (id) on delete cascade,
    ticket_type_id    uuid not null references ticket_types (id),
    quantity          integer not null check (quantity > 0),
    unit_price_paise  bigint not null check (unit_price_paise >= 0)
);
create index booking_items_booking_idx on booking_items (booking_id);
create index booking_items_ticket_type_idx on booking_items (ticket_type_id);

create table payments (
    id                   uuid primary key default gen_random_uuid(),
    booking_id           uuid not null references bookings (id),
    razorpay_order_id    text not null unique,
    razorpay_payment_id  text unique,
    amount_paise         bigint not null check (amount_paise >= 0),
    gateway_fee_paise    bigint,
    gateway_tax_paise    bigint,
    status               text not null default 'CREATED'
                             check (status in ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED')),
    method               text,
    failure_reason       text,
    raw                  jsonb,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now(),
    version              bigint not null default 0
);
create index payments_booking_idx on payments (booking_id);
create index payments_status_idx on payments (status);
create trigger payments_updated_at before update on payments for each row execute function set_updated_at();

create table refunds (
    id                  uuid primary key default gen_random_uuid(),
    payment_id          uuid not null references payments (id),
    booking_id          uuid not null references bookings (id),
    razorpay_refund_id  text unique,
    amount_paise        bigint not null check (amount_paise > 0),
    reason              text,
    status              text not null default 'REQUESTED'
                            check (status in ('REQUESTED', 'PENDING_APPROVAL', 'DECLINED', 'PROCESSING',
                                              'PROCESSED', 'FAILED')),
    requested_by        uuid references users (id),
    decided_by          uuid references users (id),
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now(),
    version             bigint not null default 0
);
create index refunds_payment_idx on refunds (payment_id);
create index refunds_booking_idx on refunds (booking_id);
create index refunds_status_idx on refunds (status);
create index refunds_requested_by_idx on refunds (requested_by);
create index refunds_decided_by_idx on refunds (decided_by);
create trigger refunds_updated_at before update on refunds for each row execute function set_updated_at();

create table disputes (
    id                   uuid primary key default gen_random_uuid(),
    payment_id           uuid not null references payments (id),
    razorpay_dispute_id  text not null unique,
    amount_paise         bigint not null,
    reason               text,
    status               text not null,
    raw                  jsonb,
    created_at           timestamptz not null default now(),
    updated_at           timestamptz not null default now()
);
create index disputes_payment_idx on disputes (payment_id);
create trigger disputes_updated_at before update on disputes for each row execute function set_updated_at();

create table webhook_events (
    id            uuid primary key default gen_random_uuid(),
    provider      text not null,
    event_id      text not null,
    type          text not null,
    payload       jsonb not null,
    received_at   timestamptz not null default now(),
    processed_at  timestamptz,
    unique (provider, event_id)
);
