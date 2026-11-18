/*
 * Copyright (c) 2025 Donato Corbacio. All rights reserved.
 * Licensed under the terms of the LICENSE file at the repository root.
 */

package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;

/**
 * OWASP Secure Code Review Cheat Sheet (controls at the controller, service
 * and repository layers), denied by default like the other guards in this
 * package: a controller never reaches a repository. It goes through a
 * service, where the checks are, the organization scope ({@code TenantContext}),
 * the record-level access ({@code ContractAccessGuard}), the transaction. A
 * controller that read or wrote rows itself would skip them, and fails here.
 */
class LayeringGuardTest {

    private static final Path MAIN = Path.of("src/main/java");
    private static final String REPOSITORY_PACKAGE = "com.donatodev.bcm_backend.repository.";

    @Test
    @DisplayName("no controller imports a repository")
    void noControllerImportsARepository() throws IOException {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (Path file : controllers()) {
            List<String> imports = Files.readAllLines(file).stream()
                    .map(String::strip)
                    .filter(line -> line.startsWith("import " + REPOSITORY_PACKAGE))
                    .toList();
            if (!imports.isEmpty()) {
                offenders.put(MAIN.relativize(file).toString(), imports);
            }
        }

        assertThat(offenders).as("call a service instead, where the checks are").isEmpty();
    }

    @Test
    @DisplayName("no controller holds a repository, whatever its package")
    void noControllerHoldsARepository() throws IOException, ClassNotFoundException {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (Path file : controllers()) {
            Class<?> controller = Class.forName(className(file));
            List<String> found = new ArrayList<>();
            for (Field field : controller.getDeclaredFields()) {
                if (Repository.class.isAssignableFrom(field.getType())) {
                    found.add("field " + field.getName());
                }
            }
            for (Constructor<?> constructor : controller.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    if (Repository.class.isAssignableFrom(parameter)) {
                        found.add("constructor parameter " + parameter.getSimpleName());
                    }
                }
            }
            if (!found.isEmpty()) {
                offenders.put(controller.getSimpleName(), found);
            }
        }

        assertThat(offenders).as("call a service instead, where the checks are").isEmpty();
    }

    @Test
    @DisplayName("the guard sees every controller")
    void theGuardSeesEveryController() throws IOException {
        // 26 in controller/ and AuthController: a scan that found none would pass the two tests above.
        assertThat(controllers()).hasSizeGreaterThanOrEqualTo(27)
                .anySatisfy(file -> assertThat(file.getFileName()).hasToString("AuthController.java"));
    }

    private static List<Path> controllers() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("@RestController") || source.contains("@Controller\n")
                                    || source.contains("@Controller\r\n");
                        } catch (IOException e) {
                            throw new java.io.UncheckedIOException(e);
                        }
                    })
                    .sorted()
                    .toList();
        }
    }

    private static String className(Path file) {
        String relative = MAIN.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
    }
}
