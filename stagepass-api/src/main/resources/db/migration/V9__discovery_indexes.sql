-- Indexes for public discovery queries.
create index events_public_idx on events (status, ends_at);
create index venues_city_trgm_idx on venues using gin (lower(city) gin_trgm_ops);
create index venues_name_trgm_idx on venues using gin (lower(name) gin_trgm_ops);
