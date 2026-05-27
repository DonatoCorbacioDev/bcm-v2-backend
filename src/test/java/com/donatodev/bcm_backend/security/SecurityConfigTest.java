package com.donatodev.bcm_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@DisplayName("SecurityConfig")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("Public endpoints")
    class PublicEndpoints {

        @Test
        @DisplayName("POST /auth/login should be accessible — wrong credentials yield 401 from business logic, not 403 security block")
        void loginShouldBePublic() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"nobody\",\"password\":\"wrong\"}"))
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("GET /actuator/health should be accessible without authentication")
        void actuatorHealthShouldBePublic() throws Exception {
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("OPTIONS preflight request should always be permitted")
        void optionsPreflightShouldBePermitted() throws Exception {
            mockMvc.perform(options("/contracts")
                            .header("Origin", "http://localhost:3000")
                            .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("Protected endpoints")
    class ProtectedEndpoints {

        @Test
        @DisplayName("GET /contracts without token should return 401")
        void contractsRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/contracts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /users without token should return 401")
        void usersRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/users"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /managers without token should return 401")
        void managersRequiresAuthentication() throws Exception {
            mockMvc.perform(get("/managers"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Authenticated access")
    class AuthenticatedAccess {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /contracts with valid authentication should not return 401")
        void contractsWithAuthShouldPass() throws Exception {
            mockMvc.perform(get("/contracts"))
                    .andExpect(result ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus(), "Should not be 401 Unauthorized"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("GET /users with valid authentication should not return 401")
        void usersWithAuthShouldPass() throws Exception {
            mockMvc.perform(get("/users"))
                    .andExpect(result ->
                        org.junit.jupiter.api.Assertions.assertNotEquals(401,
                            result.getResponse().getStatus(), "Should not be 401 Unauthorized"));
        }
    }
}
