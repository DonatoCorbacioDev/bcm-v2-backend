package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * OWASP API7 (Server-Side Request Forgery), denied by default: the backend
 * sends requests only from the places listed below, each to a destination
 * fixed by configuration. A new HTTP client anywhere else fails this test, so
 * a feature such as "import from a URL" can't ship without someone deciding
 * how its URLs are validated (an allowlist, no redirects, no private
 * addresses) and adding it here with the reason.
 */
class OutboundRequestsTest {

    private static final Path SOURCES = Path.of("src", "main", "java");

    /** Ways to open a connection from Java or Spring. */
    private static final Pattern OUTBOUND = Pattern.compile(
            "new RestTemplate\\(|RestTemplateBuilder|RestClient\\.(create|builder)|WebClient"
                    + "|HttpClient\\.new|java\\.net\\.http|new URL\\(|\\.toURL\\(\\)|openConnection\\("
                    + "|openStream\\(|new Socket\\(|restTemplate\\.[a-zA-Z]+\\(");

    /** File (relative to src/main/java/com/donatodev/bcm_backend) -> why it may open a connection. */
    private static final Map<String, String> ALLOWED = Map.of(
            "config/RestTemplateConfig.java",
            "builds the one client for the ML service: no redirects, URL from ml.fastapi.url (OutboundDestinations)",
            "service/MlProxyService.java",
            "calls the ML service at fixed paths; users pass only numbers, ids and a question in the JSON body");

    @Test
    @DisplayName("only the listed classes open connections, and every listed class still does")
    void outboundRequestsOnlyWhereListed() throws IOException {
        Map<String, Set<String>> found = new TreeMap<>();
        Path root = SOURCES.resolve(Path.of("com", "donatodev", "bcm_backend"));
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                for (String line : codeLines(Files.readAllLines(file, StandardCharsets.UTF_8))) {
                    Matcher matcher = OUTBOUND.matcher(line);
                    while (matcher.find()) {
                        found.computeIfAbsent(relative, k -> new TreeSet<>()).add(matcher.group());
                    }
                }
            }
        }

        Set<String> unexpected = new TreeSet<>(found.keySet());
        unexpected.removeAll(ALLOWED.keySet());
        assertThat(unexpected).as("classes opening connections that are not on the list: %s", found).isEmpty();

        Set<String> stale = new TreeSet<>(ALLOWED.keySet());
        stale.removeAll(found.keySet());
        assertThat(stale).as("entries to remove from ALLOWED").isEmpty();
    }

    @Test
    @DisplayName("the scan sees through formatting but not comments")
    void scanIgnoresComments() {
        List<String> code = codeLines(List.of(
                "// a RestTemplate built with new RestTemplate(factory) would be found here",
                "/* new URL(x) in a block",
                "   comment */ var c = url.openConnection();",
                " * javadoc: restTemplate.exchange(...)"));

        assertThat(code).hasSize(1);
        assertThat(OUTBOUND.matcher(code.getFirst()).find()).isTrue();
    }

    /** The lines of a source file without comments, which mention clients without opening any. */
    static List<String> codeLines(List<String> lines) {
        boolean inBlock = false;
        List<String> code = new java.util.ArrayList<>();
        for (String raw : lines) {
            String line = raw;
            if (inBlock) {
                int end = line.indexOf("*/");
                if (end < 0) {
                    continue;
                }
                line = line.substring(end + 2);
                inBlock = false;
            }
            int start = line.indexOf("/*");
            if (start >= 0) {
                int end = line.indexOf("*/", start + 2);
                if (end < 0) {
                    inBlock = true;
                    line = line.substring(0, start);
                } else {
                    line = line.substring(0, start) + line.substring(end + 2);
                }
            }
            int lineComment = line.indexOf("//");
            if (lineComment >= 0) {
                line = line.substring(0, lineComment);
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("*")) {
                code.add(line);
            }
        }
        return code;
    }
}
