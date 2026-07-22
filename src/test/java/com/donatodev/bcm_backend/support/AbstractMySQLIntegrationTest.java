package com.donatodev.bcm_backend.support;

import org.junit.jupiter.api.Tag;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for integration tests (*IT.java, run via `mvn verify`/failsafe, never
 * by `mvn test`) that need to exercise real MySQL 8.0 behavior the H2
 * "MySQL mode" used by the fast unit suite can't guarantee — real Flyway
 * migrations, real ENUM/JSON column semantics, real FK constraints.
 *
 * The container is a single static instance shared by every subclass in the
 * same JVM (Testcontainers' "singleton container" pattern): it starts once,
 * Flyway migrates it once, and Spring's test context cache reuses the same
 * ApplicationContext across IT classes since they all resolve to the same
 * dynamic properties. Requires a running Docker daemon.
 */
@Testcontainers
@ActiveProfiles("test")
@Tag("integration")
public abstract class AbstractMySQLIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("bcm_it")
            .withUsername("bcm_it")
            .withPassword("bcm_it_password");

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
    }
}
