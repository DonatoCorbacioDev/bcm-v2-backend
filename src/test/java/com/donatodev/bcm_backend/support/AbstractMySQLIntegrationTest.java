package com.donatodev.bcm_backend.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Base for integration tests (*IT.java, run via `mvn verify`/failsafe, never
 * by `mvn test`) that need to exercise real MySQL 8.4 behavior the H2
 * "MySQL mode" used by the fast unit suite can't guarantee — real Flyway
 * migrations, real ENUM/JSON column semantics, real FK constraints.
 *
 * The container is a single static instance shared by every subclass in the
 * same JVM. Deliberately NOT annotated with JUnit 5's {@code @Container}:
 * that annotation makes the {@code @Testcontainers} extension stop the
 * container in whichever subclass's {@code afterAll} runs first, since each
 * test class gets its own extension-context callback -- the next subclass
 * then restarts it on a *different* random host port, but any Spring
 * ApplicationContext already cached from an earlier subclass (Spring reuses
 * one context across test classes with identical config, e.g. two
 * {@code @DataJpaTest} classes here) keeps pointing at the now-dead old
 * port, since {@code @DynamicPropertySource} is only evaluated once per
 * context. The result is a real, but misleading, "Connection refused" --
 * discovered 2026-09-15 when a second {@code @DataJpaTest} IT class was
 * added and started intermittently failing depending on run order, never
 * on its own. Starting the container ourselves in a static initializer
 * (Testcontainers' documented "singleton container" pattern) makes it live
 * for the whole JVM regardless of which subclass runs when; only Ryuk (or
 * JVM exit) ever stops it.
 */
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractMySQLIntegrationTest {

    /** Production's version: bcm-v2-docker/mysql/Dockerfile builds on it. */
    public static final String MYSQL_IMAGE = "mysql:8.4.13";

    // Server-level collation must match the real setup (CLAUDE.md's
    // `CREATE DATABASE ... COLLATE utf8mb4_unicode_ci`), or the auto-created
    // "bcm_it" database gets MySQL 8's own compiled-in default
    // (utf8mb4_0900_ai_ci) instead — which then clashes with migrations that
    // explicitly declare utf8mb4_unicode_ci (e.g. V37's `counterparties`
    // table) with "Illegal mix of collations" the moment a query compares a
    // column from each.
    public static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_IMAGE)
            .withDatabaseName("bcm_it")
            .withUsername("bcm_it")
            .withPassword("bcm_it_password")
            // No binary log, as in docker-compose.yml: production runs without
            // one, and so the owner account, which has no SUPER, creates V60's
            // trigger with no extra setting.
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci",
                    "--skip-log-bin");

    /**
     * The account the application runs with, as in production (OWASP SQL
     * Injection Prevention Cheat Sheet, least privilege): it reads and writes
     * rows and nothing else. Only Flyway uses the owner account above, which
     * can change the schema. Every integration test runs on this one, so a
     * feature needing more than these privileges fails here, not in production.
     */
    public static final String APP_USER = "bcm_it_app";
    public static final String APP_PASSWORD = "bcm_it_app_password";
    /** The same privileges ops/mysql/create-app-runtime-user.sh grants in production. */
    public static final String APP_PRIVILEGES = "SELECT, INSERT, UPDATE, DELETE";

    static {
        MYSQL.start();
        createAppAccount();
    }

    /**
     * A connection as the schema owner, for what in production runs as root
     * outside the application (the demo reset's seed script), never through it.
     */
    protected static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static void createAppAccount() {
        try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
                Statement sql = root.createStatement()) {
            sql.execute("CREATE USER IF NOT EXISTS '" + APP_USER + "'@'%' IDENTIFIED BY '" + APP_PASSWORD + "'");
            sql.execute("GRANT " + APP_PRIVILEGES + " ON `" + MYSQL.getDatabaseName() + "`.* TO '" + APP_USER + "'@'%'");
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the application's MySQL account", e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        // The schema is built by the owner account, as in production.
        registry.add("spring.flyway.user", MYSQL::getUsername);
        registry.add("spring.flyway.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        // Override the "test" profile's H2/no-Flyway defaults: real schema,
        // built by the real migrations, validated (not generated) by Hibernate.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.platform", () -> "mysql");
        // The "test" profile sets this to true for the H2/data.sql flow; with
        // Flyway actually enabled it creates a circular depends-on between
        // the flyway and entityManagerFactory beans at context startup.
        registry.add("spring.jpa.defer-datasource-initialization", () -> "false");
        // The "test" profile's 5s Hikari connection-timeout is tuned for
        // instant-connect H2, not a real MySQL container competing for CPU on
        // a loaded CI runner (especially after ~1500 unit tests already ran
        // in the same `mvn verify`) -- match prod's 30s instead.
        registry.add("spring.datasource.hikari.connection-timeout", () -> "30000");
    }
}
