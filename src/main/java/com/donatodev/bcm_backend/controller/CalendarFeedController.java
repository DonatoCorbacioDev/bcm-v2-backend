package com.donatodev.bcm_backend.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.dto.CalendarFeedUrlDTO;
import com.donatodev.bcm_backend.service.CalendarFeedService;

@RestController
public class CalendarFeedController {

    private static final MediaType TEXT_CALENDAR = new MediaType("text", "calendar", StandardCharsets.UTF_8);

    private final CalendarFeedService calendarFeedService;

    public CalendarFeedController(CalendarFeedService calendarFeedService) {
        this.calendarFeedService = calendarFeedService;
    }

    @GetMapping("/users/me/calendar-feed")
    public ResponseEntity<CalendarFeedUrlDTO> getFeedUrl() {
        return ResponseEntity.ok(new CalendarFeedUrlDTO(calendarFeedService.getOrCreateFeedUrl()));
    }

    @PostMapping("/users/me/calendar-feed/regenerate")
    public ResponseEntity<CalendarFeedUrlDTO> regenerateFeedUrl() {
        return ResponseEntity.ok(new CalendarFeedUrlDTO(calendarFeedService.regenerateFeedUrl()));
    }

    @GetMapping("/calendar/{token}.ics")
    public ResponseEntity<String> getIcsFeed(@PathVariable String token) {
        String ics = calendarFeedService.buildIcsFeed(token);
        return ResponseEntity.ok()
                .contentType(TEXT_CALENDAR)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"bcm-contracts.ics\"")
                .body(ics);
    }
}
