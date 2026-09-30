-- Runs once on first container init (empty data volume).
-- Creates the least-privilege application user that the Spring app connects as.

CREATE USER receipttrust WITH PASSWORD 'receipttrust';

-- The receipttrust database already exists (POSTGRES_DB). Grant the app user
-- ownership-level access so Flyway can create and manage the schema objects.
GRANT ALL PRIVILEGES ON DATABASE receipttrust TO receipttrust;

-- Ensure the app user owns the public schema so migrations can create tables.
\connect receipttrust
GRANT ALL ON SCHEMA public TO receipttrust;
ALTER SCHEMA public OWNER TO receipttrust;
