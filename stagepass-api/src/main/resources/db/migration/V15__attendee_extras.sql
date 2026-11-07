-- Plan 9: notifications, reminders, saved events, reviews, profile preference.

alter table notifications add column dedupe_key text;
create unique index notifications_dedupe_uq on notifications (dedupe_key);

alter table users add column email_reminders boolean not null default true;

alter table bookings add column reminder_sent_at timestamptz;
create index bookings_reminder_idx on bookings (event_id) where reminder_sent_at is null;

create index saved_events_user_created_idx on saved_events (user_id, created_at desc);

create index reviews_event_created_idx on reviews (event_id, created_at desc);
