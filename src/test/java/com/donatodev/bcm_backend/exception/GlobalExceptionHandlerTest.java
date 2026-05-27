package com.donatodev.bcm_backend.exception;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.security.SecurityConfig;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SecurityConfig.class)
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("404 Not Found handlers")
    class NotFoundHandlers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown contract")
        void shouldReturn404ForUnknownContract() throws Exception {
            mockMvc.perform(get("/contracts/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.timestamp").exists());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown manager")
        void shouldReturn404ForUnknownManager() throws Exception {
            mockMvc.perform(get("/managers/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown user")
        void shouldReturn404ForUnknownUser() throws Exception {
            mockMvc.perform(get("/users/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown business area")
        void shouldReturn404ForUnknownBusinessArea() throws Exception {
            mockMvc.perform(get("/business-areas/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown financial type")
        void shouldReturn404ForUnknownFinancialType() throws Exception {
            mockMvc.perform(get("/financial-types/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown financial value")
        void shouldReturn404ForUnknownFinancialValue() throws Exception {
            mockMvc.perform(get("/financial-values/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 404 for unknown role")
        void shouldReturn404ForUnknownRole() throws Exception {
            mockMvc.perform(get("/roles/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.status").value(404));
        }
    }

    @Nested
    @DisplayName("400 Bad Request handlers")
    class BadRequestHandlers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Should return 400 for malformed request body")
        void shouldReturn400ForMalformedBody() throws Exception {
            mockMvc.perform(post("/business-areas")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("not-valid-json{"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Should return 400 for validation errors on login with missing fields")
        void shouldReturn400ForValidationError() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400));
        }
    }

    @Nested
    @DisplayName("401/403 Security handlers")
    class SecurityHandlers {

        @Test
        @DisplayName("Should return 401 when no authentication provided")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/contracts"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Should return 403 when role is insufficient")
        void shouldReturn403WhenInsufficientRole() throws Exception {
            mockMvc.perform(delete("/users/1"))
                    .andExpect(status().isForbidden());
        }
    }
}
