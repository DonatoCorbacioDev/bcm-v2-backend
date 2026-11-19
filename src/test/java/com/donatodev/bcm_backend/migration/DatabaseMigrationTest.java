package com.donatodev.bcm_backend.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Where the migration step takes the owner's password: the variable, else the Docker secret file. */
class DatabaseMigrationTest {

    @Test
    @DisplayName("the DB_PASSWORD variable, when set")
    void fromTheVariable() {
        assertThat(DatabaseMigration.password(Map.of("DB_PASSWORD", "from-variable"))).isEqualTo("from-variable");
    }

    @Test
    @DisplayName("otherwise the secret file, without its trailing line break")
    void fromTheSecretFile(@TempDir Path secrets) throws IOException {
        Files.writeString(secrets.resolve("DB_PASSWORD"), "from-file\n");

        assertThat(DatabaseMigration.password(Map.of("SECRETS_DIR", secrets.toString()))).isEqualTo("from-file");
        assertThat(DatabaseMigration.password(Map.of("DB_PASSWORD", "", "SECRETS_DIR", secrets.toString())))
                .isEqualTo("from-file");
    }

    @Test
    @DisplayName("neither: it refuses to start, naming what is missing")
    void neither(@TempDir Path empty) {
        assertThatThrownBy(() -> DatabaseMigration.password(Map.of("SECRETS_DIR", empty.toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("without DB_URL or DB_USERNAME it refuses to start, before touching any database")
    void requiredSettings() {
        assertThatThrownBy(() -> DatabaseMigration.migrate(Map.of("DB_USERNAME", "u", "DB_PASSWORD", "p")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_URL");
        assertThatThrownBy(() -> DatabaseMigration.migrate(Map.of("DB_URL", "jdbc:mysql://x/y", "DB_PASSWORD", "p")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_USERNAME");
    }
}
