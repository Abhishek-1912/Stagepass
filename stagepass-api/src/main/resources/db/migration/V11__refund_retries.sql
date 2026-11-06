-- Refunds Razorpay rejected (or never confirmed) are retried by a job instead of reviving tickets.
alter table refunds
    add column attempts integer not null default 0,
    add column needs_retry boolean not null default false;
create index refunds_retry_idx on refunds (status) where needs_retry;
