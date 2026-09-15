package com.donatodev.bcm_backend.support;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base for integration tests (*IT.java, run via `mvn verify`/failsafe, never
 * by `mvn test`) that need to exercise real MySQL 8.0 behavior the H2
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

    // Server-level collation must match the real setup (CLAUDE.md's
    // `CREATE DATABASE ... COLLATE utf8mb4_unicode_ci`), or the auto-created
    // "bcm_it" database gets mysql:8.0's own compiled-in default
    // (utf8mb4_0900_ai_ci) instead — which then clashes with migrations that
    // explicitly declare utf8mb4_unicode_ci (e.g. V37's `counterparties`
    // table) with "Illegal mix of collations" the moment a query compares a
    // column from each.
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bcm_it")
            .withUsername("bcm_it")
            .withPassword("bcm_it_password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
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
