/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.mysql.MySQLContainer;

import com.donatodev.bcm_backend.util.TokenHash;

/**
 * V59 on real MySQL: reset and invitation links sent before it are stored as
 * their SHA-256 by {@code SHA2(token, 256)}, and the backend, which now hashes
 * what it receives with {@link TokenHash}, still finds them: the emails already
 * sent keep working.
 */
@Tag("integration")
class TokenHashMigrationIT {

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0")
            .withDatabaseName("bcm_tokens")
            .withUsername("bcm_it")
            .withPassword("bcm_it_password")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");

    static {
        MYSQL.start();
    }

    private static final String ORG = "00000000-0000-4000-8000-000000000001";
    private static final String MANAGER = "00000000-0000-4000-8000-000000000002";
    private static final String USER = "00000000-0000-4000-8000-000000000003";
    private static final String RESET_LINK = "6f1c2b9e-3d4a-4e5f-8a7b-9c0d1e2f3a4b";
    private static final String INVITE_LINK = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d";

    @Test
    void linksSentBeforeV59KeepWorking() throws Exception {
        migrate("58");
        try (Connection c = connect(); Statement s = c.createStatement()) {
            s.execute("INSERT INTO organizations (id, name, slug, subscription_tier) VALUES ('" + ORG + "', 'Alfa', 'alfa', 'FREE')");
            s.execute("INSERT INTO managers (id, first_name, last_name, email, organization_id) VALUES ('"
                    + MANAGER + "', 'Anna', 'Alfa', 'anna@alfa.test', '" + ORG + "')");
            s.execute("INSERT INTO users (id, username, password_hash, manager_id, role_id, verified, organization_id) "
                    + "SELECT '" + USER + "', 'anna', 'h', '" + MANAGER + "', id, 1, '" + ORG + "' FROM roles LIMIT 1");
            s.execute("INSERT INTO password_reset_token (id, token, expiry_date, user_id) VALUES "
                    + "('00000000-0000-4000-8000-000000000004', '" + RESET_LINK + "', '2030-01-01 00:00:00', '" + USER + "')");
            s.execute("INSERT INTO invite_token (id, token, expiry_date, username, role, manager_id, used) VALUES "
                    + "('00000000-0000-4000-8000-000000000005', '" + INVITE_LINK + "', '2030-01-01 00:00:00', 'new', 'MANAGER', '"
                    + MANAGER + "', 0)");
        }

        migrate(null);

        // What the backend looks up for the token in the emailed link is what V59 stored.
        assertThat(single("SELECT token FROM password_reset_token")).isEqualTo(TokenHash.sha256Hex(RESET_LINK));
        assertThat(single("SELECT token FROM invite_token")).isEqualTo(TokenHash.sha256Hex(INVITE_LINK));
    }

    private static void migrate(String target) {
        var config = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            config.target(MigrationVersion.fromVersion(target));
        }
        config.load().migrate();
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
    }

    private static String single(String sql) throws SQLException {
        try (Connection c = connect(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            assertThat(r.next()).isTrue();
            return r.getString(1);
        }
    }
}
