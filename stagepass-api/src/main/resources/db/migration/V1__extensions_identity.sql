-- Stagepass schema: extensions, shared helpers, identity.
-- Supabase owns auth.users; our users table is keyed by the same UUID but has no cross-schema FK.

create extension if not exists pgcrypto;
create extension if not exists pg_trgm;

create or replace function set_updated_at() returns trigger
language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create table users (
    id          uuid primary key,
    email       text unique,
    full_name   text,
    phone       text,
    avatar_url  text,
    city        text,
    status      text not null default 'ACTIVE' check (status in ('ACTIVE', 'SUSPENDED')),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    version     bigint not null default 0
);
create trigger users_updated_at before update on users for each row execute function set_updated_at();

create table roles (
    id    smallint generated always as identity primary key,
    name  text not null unique check (name in ('ATTENDEE', 'ORGANIZER', 'ADMIN'))
);

create table user_roles (
    user_id     uuid not null references users (id) on delete cascade,
    role_id     smallint not null references roles (id),
    granted_at  timestamptz not null default now(),
    primary key (user_id, role_id)
);
create index user_roles_role_idx on user_roles (role_id);

create table organizers (
    id                         uuid primary key default gen_random_uuid(),
    user_id                    uuid not null unique references users (id),
    org_name                   text not null,
    slug                       text not null unique,
    bio                        text,
    logo_url                   text,
    contact_email              text not null,
    contact_phone              text,
    website                    text,
    pan                        text not null,
    gstin                      text,
    state                      text,
    status                     text not null default 'PENDING'
                                   check (status in ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    rejection_reason           text,
    payout_account_name        text,
    payout_account_number_enc  text,
    payout_ifsc                text,
    commission_bps_override    integer check (commission_bps_override between 0 and 10000),
    created_at                 timestamptz not null default now(),
    updated_at                 timestamptz not null default now(),
    version                    bigint not null default 0
);
create index organizers_status_idx on organizers (status);
create trigger organizers_updated_at before update on organizers for each row execute function set_updated_at();
