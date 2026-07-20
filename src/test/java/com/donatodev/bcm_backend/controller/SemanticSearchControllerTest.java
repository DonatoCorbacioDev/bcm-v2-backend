package com.donatodev.bcm_backend.controller;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.SemanticSearchResultDTO;
import com.donatodev.bcm_backend.service.SemanticSearchService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticSearchControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private SemanticSearchService semanticSearchService;

    @Nested
    @DisplayName("POST /contracts/search/semantic")
    class Search {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Returns ranked results for an authorized query")
        void shouldReturnResults() throws Exception {
            when(semanticSearchService.search(anyString(), anyInt())).thenReturn(List.of(
                    new SemanticSearchResultDTO(1L, "CTR-001", "Acme", 10L, "contract.pdf", 0.87)));

            mockMvc.perform(post("/contracts/search/semantic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"penalty clause\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].contractNumber").value("CTR-001"))
                    .andExpect(jsonPath("$[0].score").value(0.87));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Blank query is rejected with 400")
        void shouldRejectBlankQuery() throws Exception {
            mockMvc.perform(post("/contracts/search/semantic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Unauthenticated request returns 401")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(post("/contracts/search/semantic")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"query\":\"penalty clause\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
