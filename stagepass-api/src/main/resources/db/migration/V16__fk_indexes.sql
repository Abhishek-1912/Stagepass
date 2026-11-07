-- Plan 10: every foreign key gets an index starting with its column (SchemaConstraintsTest).

create index platform_settings_updated_by_idx on platform_settings (updated_by);
