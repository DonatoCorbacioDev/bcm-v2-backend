package com.donatodev.bcm_backend.controller;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.donatodev.bcm_backend.dto.NotificationDTO;
import com.donatodev.bcm_backend.entity.NotificationType;
import com.donatodev.bcm_backend.exception.NotificationNotFoundException;
import com.donatodev.bcm_backend.service.NotificationService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private NotificationService notificationService;

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("GET /notifications")
    @SuppressWarnings("unused")
    class GetNotifications {

        @Test
        @Order(1)
        @DisplayName("Should return 200 with empty list when no notifications")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn200WithEmptyList() throws Exception {
            when(notificationService.getForCurrentUser()).thenReturn(Collections.emptyList());

            mockMvc.perform(get("/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @Order(2)
        @DisplayName("Should return 200 with notification list")
        @WithMockUser(roles = "MANAGER")
        void shouldReturn200WithNotifications() throws Exception {
            NotificationDTO dto = new NotificationDTO(
                    1L, "Test Title", "Test Message",
                    NotificationType.WARNING, false, LocalDateTime.now());

            when(notificationService.getForCurrentUser()).thenReturn(List.of(dto));

            mockMvc.perform(get("/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].title").value("Test Title"))
                    .andExpect(jsonPath("$[0].type").value("WARNING"))
                    .andExpect(jsonPath("$[0].read").value(false));
        }

        @Test
        @Order(3)
        @DisplayName("Should return 401 for unauthenticated request")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(get("/notifications"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    @DisplayName("PATCH /notifications/{id}/read")
    @SuppressWarnings("unused")
    class MarkAsRead {

        @Test
        @Order(1)
        @DisplayName("Should return 204 when notification marked as read")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn204OnSuccess() throws Exception {
            doNothing().when(notificationService).markAsRead(1L);

            mockMvc.perform(patch("/notifications/1/read"))
                    .andExpect(status().isNoContent());
        }

        @Test
        @Order(2)
        @DisplayName("Should return 404 when notification not found")
        @WithMockUser(roles = "MANAGER")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new NotificationNotFoundException("Notification not found: 99"))
                    .when(notificationService).markAsRead(99L);

            mockMvc.perform(patch("/notifications/99/read"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @Order(3)
        @DisplayName("Should return 403 when access denied")
        @WithMockUser(roles = "ADMIN")
        void shouldReturn403WhenAccessDenied() throws Exception {
            doThrow(new org.springframework.security.access.AccessDeniedException("Not authorized"))
                    .when(notificationService).markAsRead(5L);

            mockMvc.perform(patch("/notifications/5/read"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @Order(4)
        @DisplayName("Should return 401 for unauthenticated request")
        void shouldReturn401WhenNotAuthenticated() throws Exception {
            mockMvc.perform(patch("/notifications/1/read"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
