-- Catalog: categories, venues, events, ticket types and inventory.

create table event_categories (
    id          uuid primary key default gen_random_uuid(),
    name        text not null,
    slug        text not null unique,
    icon        text,
    sort_order  integer not null default 0,
    active      boolean not null default true,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);
create trigger event_categories_updated_at before update on event_categories
    for each row execute function set_updated_at();

create table venues (
    id               uuid primary key default gen_random_uuid(),
    organizer_id     uuid not null references organizers (id),
    name             text not null,
    address_line     text not null,
    city             text not null,
    state            text,
    pincode          text,
    lat              double precision,
    lng              double precision,
    google_place_id  text,
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);
create index venues_organizer_idx on venues (organizer_id);
create index venues_city_idx on venues (lower(city));
create index venues_lat_lng_idx on venues (lat, lng);
create trigger venues_updated_at before update on venues for each row execute function set_updated_at();

create table events (
    id                         uuid primary key default gen_random_uuid(),
    organizer_id               uuid not null references organizers (id),
    category_id                uuid not null references event_categories (id),
    venue_id                   uuid references venues (id),
    slug                       text not null unique,
    title                      text not null,
    summary                    text,
    description                text,
    cover_image_url            text,
    gallery                    jsonb not null default '[]'::jsonb,
    starts_at                  timestamptz not null,
    ends_at                    timestamptz not null,
    timezone                   text not null default 'Asia/Kolkata',
    status                     text not null default 'DRAFT'
                                   check (status in ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED',
                                                     'CANCELLED', 'COMPLETED')),
    rejection_reason           text,
    is_featured                boolean not null default false,
    terms                      text,
    max_tickets_per_user       integer check (max_tickets_per_user > 0),
    refund_policy              text not null default 'NONE' check (refund_policy in ('NONE', 'UNTIL_HOURS_BEFORE')),
    refund_cutoff_hours        integer check (refund_cutoff_hours >= 0),
    reschedule_refund_until    timestamptz,
    convenience_fee_override   jsonb,
    commission_bps_override    integer check (commission_bps_override between 0 and 10000),
    popularity_score           double precision not null default 0,
    published_at               timestamptz,
    search_vector              tsvector generated always as (
                                   setweight(to_tsvector('simple', coalesce(title, '')), 'A') ||
                                   setweight(to_tsvector('simple', coalesce(summary, '')), 'B')
                               ) stored,
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    version                    bigint not null default 0,
    check (ends_at > starts_at)
);
create index events_organizer_idx on events (organizer_id);
create index events_category_idx on events (category_id);
create index events_venue_idx on events (venue_id);
create index events_status_starts_idx on events (status, starts_at);
create index events_featured_idx on events (is_featured) where is_featured;
create index events_search_idx on events using gin (search_vector);
create index events_title_trgm_idx on events using gin (title gin_trgm_ops);
create trigger events_updated_at before update on events for each row execute function set_updated_at();

create table ticket_types (
    id              uuid primary key default gen_random_uuid(),
    event_id        uuid not null references events (id) on delete cascade,
    name            text not null,
    description     text,
    price_paise     bigint not null check (price_paise >= 0),
    sale_starts_at  timestamptz,
    sale_ends_at    timestamptz,
    min_per_order   integer not null default 1 check (min_per_order >= 1),
    max_per_order   integer not null default 10 check (max_per_order >= 1),
    sort_order      integer not null default 0,
    status          text not null default 'ACTIVE' check (status in ('ACTIVE', 'HIDDEN', 'SOLD_OUT')),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    version         bigint not null default 0,
    check (max_per_order >= min_per_order),
    check (sale_ends_at is null or sale_starts_at is null or sale_ends_at > sale_starts_at)
);
create index ticket_types_event_idx on ticket_types (event_id);
create trigger ticket_types_updated_at before update on ticket_types for each row execute function set_updated_at();

-- Counters updated atomically by the booking module; the CHECK is the last line of defence against overselling.
create table ticket_inventory (
    ticket_type_id  uuid primary key references ticket_types (id) on delete cascade,
    total           integer not null,
    sold            integer not null default 0,
    held            integer not null default 0,
    updated_at      timestamptz not null default now(),
    check (total >= 0 and sold >= 0 and held >= 0 and sold + held <= total)
);
create trigger ticket_inventory_updated_at before update on ticket_inventory
    for each row execute function set_updated_at();
