package com.donatodev.bcm_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.donatodev.bcm_backend.service.MlProxyService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MlProxyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MlProxyService mlProxyService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /forecast and /risk-scores")
    @SuppressWarnings("unused")
    class ProxyEndpoints {

        @Test
        @Order(1)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Admin can fetch forecast, default months=3")
        void shouldReturnForecastForAdmin() throws Exception {
            when(mlProxyService.getForecast(3)).thenReturn(
                    ResponseEntity.status(HttpStatus.OK)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body("{\"historical\":[]}"));

            mockMvc.perform(get("/forecast"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"historical\":[]}"));

            verify(mlProxyService).getForecast(3);
        }

        @Test
        @Order(2)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager can fetch forecast with a custom months value")
        void shouldForwardCustomMonthsForManager() throws Exception {
            when(mlProxyService.getForecast(anyInt())).thenReturn(
                    ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("{}"));

            mockMvc.perform(get("/forecast?months=6"))
                    .andExpect(status().isOk());

            verify(mlProxyService).getForecast(6);
        }

        @Test
        @Order(3)
        @WithMockUser(roles = "MANAGER")
        @DisplayName("Manager can fetch risk scores")
        void shouldReturnRiskScoresForManager() throws Exception {
            when(mlProxyService.getRiskScores()).thenReturn(
                    ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("[]"));

            mockMvc.perform(get("/risk-scores"))
                    .andExpect(status().isOk())
                    .andExpect(content().json("[]"));
        }

        @Test
        @Order(4)
        @DisplayName("Unauthenticated request to forecast is denied")
        void shouldReturn401ForUnauthenticatedForecast() throws Exception {
            mockMvc.perform(get("/forecast"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(5)
        @DisplayName("Unauthenticated request to risk-scores is denied")
        void shouldReturn401ForUnauthenticatedRiskScores() throws Exception {
            mockMvc.perform(get("/risk-scores"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @Order(6)
        @WithMockUser(roles = "ADMIN")
        @DisplayName("Propagates 503 when the ML service is unavailable")
        void shouldPropagateServiceUnavailable() throws Exception {
            when(mlProxyService.getRiskScores()).thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());

            mockMvc.perform(get("/risk-scores"))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
