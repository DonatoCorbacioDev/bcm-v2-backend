package com.donatodev.bcm_backend.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * OWASP Multi-Tenant Security Cheat Sheet, deny by default: the bean that
 * wipes every organization's data is never created outside the tests.
 */
class TestDataCleanerProfileTest {

    @Configuration
    @Import(TestDataCleaner.class)
    static class WithTheCleaner {
    }

    @ParameterizedTest
    @ValueSource(strings = { "prod", "dev", "default" })
    void doesNotExistOutsideTheTests(String profile) {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withUserConfiguration(WithTheCleaner.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(TestDataCleaner.class));
    }

    @org.junit.jupiter.api.Test
    void isMeantOnlyForTheTestProfile() {
        assertThat(TestDataCleaner.class.getAnnotation(org.springframework.context.annotation.Profile.class).value())
                .containsExactly(TestDataCleaner.PROFILE);
        assertThat(TestDataCleaner.PROFILE).isEqualTo("test");
    }
}
