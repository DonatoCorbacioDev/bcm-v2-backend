package com.donatodev.bcm_backend.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import com.donatodev.bcm_backend.support.AbstractMySQLIntegrationTest;

/**
 * OWASP SQL Injection Prevention Cheat Sheet, least privilege, against real
 * MySQL: the account the application runs with reads and writes rows and can
 * do nothing else. An injection that got through could not drop or change a
 * table, grant itself more, or write a file on the server. The schema is built
 * by Flyway with the owner account, as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Integration Test: the application's MySQL account has least privilege")
class LeastPrivilegeMySQLIT extends AbstractMySQLIntegrationTest {

    private final JdbcTemplate jdbc;

    @Autowired
    LeastPrivilegeMySQLIT(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    @DisplayName("the application runs as its own account, which Flyway did not use")
    void runsAsTheApplicationAccount() {
        assertThat(jdbc.queryForObject("SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)", String.class))
                .isEqualTo(APP_USER);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isPositive();
    }

    @Test
    @DisplayName("it holds exactly the row privileges, on this database only")
    void holdsOnlyRowPrivileges() {
        List<String> grants = jdbc.queryForList("SHOW GRANTS FOR CURRENT_USER()", String.class);
        assertThat(grants).hasSize(2);
        assertThat(grants).anySatisfy(g -> assertThat(g).startsWith("GRANT USAGE ON *.*"));
        assertThat(grants).anySatisfy(g -> assertThat(g)
                .startsWith("GRANT " + APP_PRIVILEGES + " ON `" + "bcm_it" + "`.*"));
    }

    @Test
    @DisplayName("it reads and writes rows")
    void readsAndWritesRows() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO organizations (id, name, slug) VALUES (?, ?, ?)", id, "Least privilege IT", id);
        jdbc.update("UPDATE organizations SET name = ? WHERE id = ?", "Renamed", id);
        assertThat(jdbc.queryForObject("SELECT name FROM organizations WHERE id = ?", String.class, id)).isEqualTo("Renamed");
        assertThat(jdbc.update("DELETE FROM organizations WHERE id = ?", id)).isEqualTo(1);
    }

    @Test
    @DisplayName("an audit log row cannot be changed, nor its guard dropped; it can still be deleted")
    void auditLogRowsCannotBeChanged() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO audit_logs (id, action, entity_type, username, details) VALUES (?, ?, ?, ?, ?)",
                id, "PASSWORD_CHANGED", "User", "mario", "original");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_logs SET details = 'rewritten' WHERE id = ?", id))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("audit_logs rows cannot be changed");
        assertThatThrownBy(() -> jdbc.execute("DROP TRIGGER audit_logs_no_update"))
                .isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
        assertThat(jdbc.queryForObject("SELECT details FROM audit_logs WHERE id = ?", String.class, id))
                .isEqualTo("original");

        // Retention and the GDPR purge of an organization delete rows.
        assertThat(jdbc.update("DELETE FROM audit_logs WHERE id = ?", id)).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @DisplayName("what an injection would try beyond rows is refused")
    @ValueSource(strings = {
            "DROP TABLE contracts",
            "TRUNCATE TABLE audit_logs",
            "ALTER TABLE users ADD COLUMN injected INT",
            "CREATE TABLE injected (id INT)",
            "CREATE INDEX injected ON users (username)",
            "GRANT ALL PRIVILEGES ON bcm_it.* TO 'bcm_it_app'@'%'",
            "CREATE USER 'intruder'@'%' IDENTIFIED BY 'x'",
            "SELECT username, password_hash FROM users INTO OUTFILE '/tmp/stolen.txt'",
            "SELECT LOAD_FILE('/etc/passwd') FROM DUAL WHERE LOAD_FILE('/etc/passwd') IS NOT NULL",
            "SELECT * FROM mysql.user"})
    void anythingBeyondRowsIsRefused(String statement) {
        if (statement.startsWith("SELECT LOAD_FILE")) {
            // Without the FILE privilege MySQL answers NULL instead of the file.
            assertThat(jdbc.queryForList(statement)).isEmpty();
            return;
        }
        assertThatThrownBy(() -> jdbc.execute(statement)).isInstanceOf(DataAccessException.class)
                .rootCause().hasMessageContaining("denied");
    }
}
