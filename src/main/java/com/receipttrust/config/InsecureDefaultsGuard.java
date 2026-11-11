package com.receipttrust.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fails fast at startup when the app points at a non-local database while still
 * using the repo's built-in development defaults (JWT signing secret or the
 * local Docker DB password). Those defaults are public, so running with them
 * against a real deployment (e.g. Supabase on Render) would let anyone forge
 * tokens or guess credentials.
 *
 * <p>Implemented as an {@link EnvironmentPostProcessor} (registered in
 * META-INF/spring.factories) so it runs before any bean is created, i.e. before
 * Flyway connects and migrates. Local Docker Postgres and the H2 dev/test
 * profiles are exempt, so plain local runs keep working with no extra env vars.
 */
public class InsecureDefaultsGuard implements EnvironmentPostProcessor, Ordered {

    /** The development-only JWT secret shipped in application.yml. */
    static final String DEV_JWT_SECRET =
            "Y2hhbmdlLW1lLXRoaXMtaXMtYS1kZXYtb25seS1zZWNyZXQtMzJieXRlcyE=";

    /** The local Docker Postgres password shipped as the DB_PASSWORD default. */
    static final String DEV_DB_PASSWORD = "receipttrust";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String dbUrl = env.getProperty("spring.datasource.url", "");
        if (isLocal(dbUrl)) {
            return;
        }
        if (DEV_JWT_SECRET.equals(env.getProperty("receipttrust.security.jwt.secret"))) {
            throw new IllegalStateException(
                    "JWT_SECRET is not set: refusing to start against a non-local database "
                            + "with the built-in development JWT secret. Set a strong, unique JWT_SECRET.");
        }
        if (DEV_DB_PASSWORD.equals(env.getProperty("spring.datasource.password"))) {
            throw new IllegalStateException(
                    "DB_PASSWORD is not set: refusing to start against a non-local database "
                            + "with the default local password. Set DB_PASSWORD.");
        }
    }

    /** Must run after application.yml / profile config has been loaded. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /** Local Docker Postgres, loopback, or an in-memory/file H2 database. */
    static boolean isLocal(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String u = url.toLowerCase();
        return u.startsWith("jdbc:h2:")
                || u.contains("//localhost")
                || u.contains("//127.0.0.1")
                || u.contains("//[::1]");
    }
}
