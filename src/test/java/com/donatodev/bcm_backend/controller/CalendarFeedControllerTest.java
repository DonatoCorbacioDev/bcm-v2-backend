package com.donatodev.bcm_backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.exception.UserNotFoundException;
import com.donatodev.bcm_backend.service.CalendarFeedService;

import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CalendarFeedControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CalendarFeedService calendarFeedService;

    @Test
    @DisplayName("GET /users/me/calendar-feed without auth returns 401")
    void shouldReturn401ForFeedUrlWithoutAuth() throws Exception {
        mockMvc.perform(get("/users/me/calendar-feed"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /users/me/calendar-feed returns the subscribe URL")
    void shouldReturnFeedUrl() throws Exception {
        when(calendarFeedService.getOrCreateFeedUrl())
                .thenReturn("http://localhost:8090/api/v1/calendar/abc123.ics");

        mockMvc.perform(get("/users/me/calendar-feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:8090/api/v1/calendar/abc123.ics"));
    }

    @Test
    @DisplayName("POST /users/me/calendar-feed/regenerate without auth returns 401")
    void shouldReturn401ForRegenerateWithoutAuth() throws Exception {
        mockMvc.perform(post("/users/me/calendar-feed/regenerate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/calendar-feed/regenerate returns a new URL")
    void shouldRegenerateFeedUrl() throws Exception {
        when(calendarFeedService.regenerateFeedUrl())
                .thenReturn("http://localhost:8090/api/v1/calendar/newtoken.ics");

        mockMvc.perform(post("/users/me/calendar-feed/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("http://localhost:8090/api/v1/calendar/newtoken.ics"));
    }

    @Test
    @DisplayName("GET /calendar/{token}.ics requires no authentication and returns text/calendar")
    void shouldServeIcsFeedWithoutAuth() throws Exception {
        when(calendarFeedService.buildIcsFeed("abc123"))
                .thenReturn("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n");

        mockMvc.perform(get("/calendar/abc123.ics"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string("BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n"));
    }

    @Test
    @DisplayName("GET /calendar/{token}.ics with an unknown token returns 404")
    void shouldReturn404ForUnknownToken() throws Exception {
        when(calendarFeedService.buildIcsFeed("bogus"))
                .thenThrow(new UserNotFoundException("Invalid calendar feed token"));

        mockMvc.perform(get("/calendar/bogus.ics"))
                .andExpect(status().isNotFound());
    }
}
