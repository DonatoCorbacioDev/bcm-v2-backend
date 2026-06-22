package com.donatodev.bcm_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.donatodev.bcm_backend.service.MlProxyService;

/**
 * Exposes the ML forecast/risk-score data to the authenticated frontend.
 * The frontend must call these endpoints instead of the FastAPI service
 * directly, so every request is authenticated and tenant-scoped.
 */
@RestController
public class MlProxyController {

    private final MlProxyService mlProxyService;

    public MlProxyController(MlProxyService mlProxyService) {
        this.mlProxyService = mlProxyService;
    }

    @GetMapping("/forecast")
    public ResponseEntity<String> getForecast(@RequestParam(defaultValue = "3") int months) {
        return mlProxyService.getForecast(months);
    }

    @GetMapping("/risk-scores")
    public ResponseEntity<String> getRiskScores() {
        return mlProxyService.getRiskScores();
    }
}
