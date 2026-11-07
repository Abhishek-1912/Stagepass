-- Plan 8: admin dashboard.

alter table users add column suspended_at timestamptz;
alter table users add column suspension_reason text;

create unique index payouts_org_reference_uq on payouts (organizer_id, lower(reference));

create unique index event_reports_open_uq on event_reports (event_id, reporter_id) where status = 'OPEN';

create index bookings_confirmed_at_idx on bookings (confirmed_at);
