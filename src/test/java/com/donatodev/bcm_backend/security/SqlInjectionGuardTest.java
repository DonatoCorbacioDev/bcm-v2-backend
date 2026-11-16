package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OWASP SQL Injection Prevention Cheat Sheet, denied by default: no SQL is
 * ever built while the application runs. Spring Data's queries are
 * annotation constants and bind every value; the one {@code JdbcTemplate}
 * runs fixed statements with {@code ?}. A new way of reaching the database,
 * or a statement assembled from a variable, fails here.
 */
class SqlInjectionGuardTest {

    private static final Path MAIN = Path.of("src/main/java/com/donatodev/bcm_backend");

    /** APIs that take SQL or JPQL as a runtime string. None is used; a new use fails here. */
    private static final Map<String, Pattern> RUNTIME_SQL = Map.of(
            "EntityManager.createQuery", Pattern.compile("\\.createQuery\\("),
            "EntityManager.createNativeQuery", Pattern.compile("\\.createNativeQuery\\("),
            "native @Query", Pattern.compile("nativeQuery\\s*=\\s*true"),
            "java.sql.Statement", Pattern.compile("\\bjava\\.sql\\.(Statement|PreparedStatement|Connection)\\b"),
            "Connection.prepareStatement", Pattern.compile("\\.prepare(Statement|Call)\\("),
            "Hibernate session SQL", Pattern.compile("\\.(createSQLQuery|createNativeMutationQuery|createMutationQuery|createSelectionQuery)\\("));

    /** The classes allowed a JdbcTemplate, and why. */
    private static final Map<String, String> JDBC_USERS = Map.of(
            "OrganizationPurgeService",
            "deletes an organization table by table: fixed statements, the organization id bound with ?");

    /** A JdbcTemplate call; its first argument is the SQL. */
    private static final Pattern JDBC_CALL = Pattern.compile(
            "\\bjdbc\\.(update|query|queryForList|queryForObject|queryForMap|execute|batchUpdate)\\(");

    /** SQL written only as string literals, joined with + over several lines at most. */
    private static final Pattern LITERALS_ONLY = Pattern.compile("\\s*\"(?:[^\"\\\\]|\\\\.)*\"(?:\\s*\\+\\s*\"(?:[^\"\\\\]|\\\\.)*\")*\\s*");

    /** Variables that hold SQL in OrganizationPurgeService, and where it comes from. */
    private static final Set<String> FIXED_SQL_VARIABLES = Set.of(
            "statement", // each of DELETE_STATEMENTS, a constant list
            "sql");      // count(String sql, ...), whose callers are checked below to pass literals

    @Test
    @DisplayName("no API that takes SQL as a runtime string is used anywhere")
    void noRuntimeSql() throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();
        for (Path file : sources()) {
            String code = Files.readString(file);
            RUNTIME_SQL.forEach((what, pattern) -> {
                if (pattern.matcher(code).find()) {
                    found.computeIfAbsent(what, k -> new TreeSet<>()).add(name(file));
                }
            });
        }
        assertThat(found).as("runtime SQL or JPQL").isEmpty();
    }

    @Test
    @DisplayName("only the listed classes use a JdbcTemplate, and only with fixed SQL")
    void jdbcRunsOnlyFixedSql() throws IOException {
        Set<String> users = new TreeSet<>();
        List<String> builtSql = new ArrayList<>();
        for (Path file : sources()) {
            String code = Files.readString(file);
            if (!code.contains("JdbcTemplate")) {
                continue;
            }
            users.add(name(file));
            for (String sql : firstArguments(code, JDBC_CALL)) {
                if (!LITERALS_ONLY.matcher(sql).matches() && !FIXED_SQL_VARIABLES.contains(sql.trim())) {
                    builtSql.add(name(file) + ": " + sql.trim());
                }
            }
            for (String sql : firstArguments(code, Pattern.compile("\\bcount\\("))) {
                if (!LITERALS_ONLY.matcher(sql).matches() && !sql.trim().startsWith("String ")) {
                    builtSql.add(name(file) + ": count(" + sql.trim() + ")");
                }
            }
        }
        assertThat(users).as("classes with a JdbcTemplate").isEqualTo(new TreeSet<>(JDBC_USERS.keySet()));
        assertThat(builtSql).as("SQL that is not a fixed string").isEmpty();
    }

    @Test
    @DisplayName("the guard recognizes SQL built from a variable")
    void guardSeesBuiltSql() {
        String code = "jdbc.update(\"DELETE FROM t WHERE name = '\" + name + \"'\");\n"
                + "jdbc.queryForList(\"SELECT id FROM t\"\n + \" WHERE org = ?\", id);";
        List<String> sql = firstArguments(code, JDBC_CALL);
        assertThat(sql).hasSize(2);
        assertThat(LITERALS_ONLY.matcher(sql.get(0)).matches()).isFalse();
        assertThat(LITERALS_ONLY.matcher(sql.get(1)).matches()).isTrue();
    }

    /** The first argument of every call matched by {@code call}, up to the first top-level comma. */
    private static List<String> firstArguments(String code, Pattern call) {
        List<String> arguments = new ArrayList<>();
        Matcher m = call.matcher(code);
        while (m.find()) {
            int depth = 0;
            boolean inString = false;
            StringBuilder argument = new StringBuilder();
            for (int i = m.end(); i < code.length(); i++) {
                char c = code.charAt(i);
                if (inString) {
                    argument.append(c);
                    if (c == '\\') {
                        argument.append(code.charAt(++i));
                    } else if (c == '"') {
                        inString = false;
                    }
                    continue;
                }
                if (c == '"') {
                    inString = true;
                } else if (c == '(') {
                    depth++;
                } else if (c == ')' && depth-- == 0 || c == ',' && depth == 0) {
                    break;
                }
                argument.append(c);
            }
            arguments.add(argument.toString());
        }
        return arguments;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    private static String name(Path file) {
        return file.getFileName().toString().replace(".java", "");
    }
}
