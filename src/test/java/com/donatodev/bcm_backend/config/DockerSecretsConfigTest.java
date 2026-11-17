package com.donatodev.bcm_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * Docker secrets (OWASP Secrets Management Cheat Sheet): application.properties
 * imports the files of the secrets directory as properties, so a secret
 * mounted as /run/secrets/JWT_SECRET is what ${JWT_SECRET} reads, with no
 * environment variable. Loads the real application.properties, with no beans.
 */
class DockerSecretsConfigTest {

    @Configuration
    static class NoBeans {
    }

    private static ConfigurableApplicationContext start(Path secretsDir) {
        return new SpringApplicationBuilder(NoBeans.class)
                .web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF)
                .logStartupInfo(false)
                .run("--app.secrets-dir=" + secretsDir);
    }

    @Test
    void eachFileOfTheSecretsDirectoryIsAPropertyNamedAfterIt(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("BCM_TEST_SECRET"), "from-the-file\n");

        try (ConfigurableApplicationContext context = start(dir)) {
            // The newline a file usually ends with is not part of the secret.
            assertThat(context.getEnvironment().getProperty("BCM_TEST_SECRET")).isEqualTo("from-the-file");
        }
    }

    @Test
    void withoutTheDirectoryTheApplicationStillStarts(@TempDir Path dir) {
        try (ConfigurableApplicationContext context = start(dir.resolve("missing"))) {
            assertThat(context.getEnvironment().getProperty("BCM_TEST_SECRET")).isNull();
        }
    }
}
