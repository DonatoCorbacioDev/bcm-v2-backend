package com.donatodev.bcm_backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("application-prod.properties")
class ProdPropertiesTest {

    private Properties loadProdProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("application-prod.properties")) {
            assertNotNull(in, "application-prod.properties must be on the classpath");
            properties.load(in);
        }
        return properties;
    }

    @Test
    @DisplayName("Swagger UI must be disabled in production")
    void swaggerUiDisabledInProd() throws IOException {
        Properties properties = loadProdProperties();
        assertEquals("false", properties.getProperty("springdoc.swagger-ui.enabled"));
    }

    @Test
    @DisplayName("API docs (OpenAPI JSON) must be disabled in production")
    void apiDocsDisabledInProd() throws IOException {
        Properties properties = loadProdProperties();
        assertEquals("false", properties.getProperty("springdoc.api-docs.enabled"));
    }
}
