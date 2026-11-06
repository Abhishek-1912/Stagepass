-- One email per business fact: enqueue with a dedupe key is a no-op when the key already exists.
alter table email_outbox add column dedupe_key text unique;
