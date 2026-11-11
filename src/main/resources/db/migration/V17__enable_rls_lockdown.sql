-- Lock the public schema down from Supabase's auto-generated REST API (PostgREST).
--
-- Supabase grants anon/authenticated access to every table in "public" by
-- default, and this app creates all of its tables there. With RLS off, anyone
-- holding the public anon key could read or modify users (password_hash),
-- refresh_tokens, messages and debts. This app never uses PostgREST: it connects
-- directly as the table-owning role, which bypasses RLS, so enabling RLS with no
-- policies is a deny-all for anon/authenticated and does NOT affect the backend.
--
-- Postgres-only (Flyway is disabled on the H2 dev/test profiles). Safe on plain
-- local Postgres, where the anon/authenticated roles don't exist: every REVOKE is
-- guarded by a pg_roles check. We deliberately do not use FORCE ROW LEVEL
-- SECURITY, which would also apply RLS to the owner and break the app.
DO $$
DECLARE
    t record;
    r text;
BEGIN
    -- flyway_schema_history is skipped on purpose: Flyway holds a transaction on
    -- it while this migration runs, so altering it here would deadlock. It is
    -- locked down after migration by FlywayHistoryLockdown instead.
    FOR t IN
        SELECT tablename FROM pg_tables
        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);

        FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
                EXECUTE format('REVOKE ALL ON TABLE public.%I FROM %I', t.tablename, r);
            END IF;
        END LOOP;
    END LOOP;

    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r);
            EXECUTE format('REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM %I', r);
            -- Tables/sequences created by this role in future migrations.
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', r);
            EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', r);
        END IF;
    END LOOP;
END
$$;

-- Force every session to sign in again: if the tables were exposed before this
-- migration, password hashes and refresh tokens may have been read. Revoking all
-- refresh tokens and bumping session_version invalidates existing sessions.
UPDATE refresh_tokens SET revoked = TRUE;
UPDATE users SET session_version = session_version + 1;
