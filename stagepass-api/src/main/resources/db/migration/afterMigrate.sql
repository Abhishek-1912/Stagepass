-- Flyway callback: runs after every migrate, so tables added by future migrations are covered too.
--
-- Supabase exposes the public schema through its Data API (PostgREST) to anyone holding the anon key,
-- which ships in the web app by design. Stagepass reads and writes data only through its own API
-- (connecting as the table owner, which bypasses RLS), so the Supabase client roles get nothing:
-- RLS is enabled with no policies, and all their privileges are revoked.
do $$
declare
    t record;
    client_role text;
begin
    for t in select tablename from pg_tables where schemaname = 'public' loop
        execute format('alter table public.%I enable row level security', t.tablename);
    end loop;

    foreach client_role in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = client_role) then
            execute format('revoke all on all tables in schema public from %I', client_role);
            execute format('revoke all on all sequences in schema public from %I', client_role);
            execute format('revoke execute on all functions in schema public from %I', client_role);
        end if;
    end loop;
end
$$;
