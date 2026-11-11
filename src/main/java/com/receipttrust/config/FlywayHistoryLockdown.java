package com.receipttrust.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Locks flyway_schema_history down from Supabase's REST API (anon/authenticated).
 *
 * <p>V17 locks every app table but cannot touch flyway_schema_history, because
 * Flyway holds a transaction on it while migrations run (altering it would
 * deadlock). Once Flyway has finished, this runner does it. It is idempotent,
 * PostgreSQL-only (a no-op on H2), and tolerates missing anon/authenticated
 * roles (plain local Postgres). Failures are logged, never fatal.
 */
@Component
public class FlywayHistoryLockdown implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayHistoryLockdown.class);

    private final JdbcTemplate jdbc;

    public FlywayHistoryLockdown(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String product = jdbc.execute((java.sql.Connection c) -> c.getMetaData().getDatabaseProductName());
            if (product == null || !product.toLowerCase().contains("postgres")) {
                return;
            }
            Boolean exists = jdbc.queryForObject(
                    "SELECT to_regclass('public.flyway_schema_history') IS NOT NULL", Boolean.class);
            if (!Boolean.TRUE.equals(exists)) {
                return;
            }
            jdbc.execute("ALTER TABLE public.flyway_schema_history ENABLE ROW LEVEL SECURITY");
            for (String role : new String[] {"anon", "authenticated"}) {
                Boolean roleExists = jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = ?)", Boolean.class, role);
                if (Boolean.TRUE.equals(roleExists)) {
                    // Role names come from the fixed list above, never from input.
                    jdbc.execute("REVOKE ALL ON TABLE public.flyway_schema_history FROM " + role);
                }
            }
        } catch (Exception e) {
            log.warn("Could not lock down flyway_schema_history (non-fatal): {}", e.getMessage());
        }
    }
}
