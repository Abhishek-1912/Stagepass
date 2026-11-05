package com.stagepass.schema;

import com.stagepass.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Supabase exposes the public schema through its Data API using the browser-visible anon key.
 * All data must go through the Stagepass API, so every table is locked against anon/authenticated.
 */
@Transactional
class DataApiLockdownTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void everyPublicTableHasRowLevelSecurity() {
        Integer unprotected = jdbc.queryForObject("""
                select count(*) from pg_class c join pg_namespace n on n.oid = c.relnamespace
                where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity
                """, Integer.class);
        assertThat(unprotected).isZero();
    }

    @Test
    void supabaseClientRolesLoseTablePrivileges() throws Exception {
        jdbc.execute("""
                do $$ begin
                  if not exists (select 1 from pg_roles where rolname = 'anon') then create role anon nologin; end if;
                  if not exists (select 1 from pg_roles where rolname = 'authenticated') then create role authenticated nologin; end if;
                end $$
                """);
        jdbc.execute("grant select, insert on public.users, public.organizers to anon, authenticated");

        String lockdown = new ClassPathResource("db/migration/afterMigrate.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(lockdown);

        assertThat(jdbc.queryForObject("select has_table_privilege('anon', 'public.users', 'SELECT')", Boolean.class))
                .isFalse();
        assertThat(jdbc.queryForObject(
                "select has_table_privilege('authenticated', 'public.organizers', 'INSERT')", Boolean.class)).isFalse();
    }
}
