package com.donatodev.bcm_backend.controller;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.dto.TotpConfirmResponse;
import com.donatodev.bcm_backend.dto.TotpSetupResponse;
import com.donatodev.bcm_backend.dto.TotpStatusResponse;
import com.donatodev.bcm_backend.service.TwoFactorAuthService;
import com.donatodev.bcm_backend.auth.CurrentSessionRenewer;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TwoFactorAuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private TwoFactorAuthService twoFactorAuthService;
    @MockitoBean private CurrentSessionRenewer currentSessionRenewer;

    @Test
    @DisplayName("GET /users/me/2fa/status without auth returns 401")
    void shouldReturn401ForStatusWithoutAuth() throws Exception {
        mockMvc.perform(get("/users/me/2fa/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /users/me/2fa/status returns the current enabled flag")
    void shouldReturnStatus() throws Exception {
        when(twoFactorAuthService.status()).thenReturn(new TotpStatusResponse(true));

        mockMvc.perform(get("/users/me/2fa/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/2fa/setup returns the secret and otpauth URI")
    void shouldSetup() throws Exception {
        when(twoFactorAuthService.setup())
                .thenReturn(new TotpSetupResponse("SECRET123", "otpauth://totp/BCM:admin?secret=SECRET123"));

        mockMvc.perform(post("/users/me/2fa/setup"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").value("SECRET123"))
                .andExpect(jsonPath("$.otpAuthUri").value("otpauth://totp/BCM:admin?secret=SECRET123"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/2fa/confirm with a valid code returns recovery codes")
    void shouldConfirm() throws Exception {
        when(twoFactorAuthService.confirm("123456"))
                .thenReturn(new TotpConfirmResponse(List.of("AAAA-1111", "BBBB-2222")));
        when(currentSessionRenewer.renewedRefreshCookie()).thenReturn("refresh_token=renewed; Path=/api/v1/auth");

        mockMvc.perform(post("/users/me/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpCodeBody("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(2))
                // Every other session ended with the change: this one gets a new refresh cookie.
                .andExpect(header().string("Set-Cookie", "refresh_token=renewed; Path=/api/v1/auth"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/2fa/confirm with an invalid code returns 400")
    void shouldReturn400WhenConfirmCodeInvalid() throws Exception {
        doThrow(new IllegalArgumentException("Invalid verification code"))
                .when(twoFactorAuthService).confirm("000000");

        mockMvc.perform(post("/users/me/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpCodeBody("000000"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/2fa/disable with a valid code returns 204")
    void shouldDisable() throws Exception {
        when(currentSessionRenewer.renewedRefreshCookie()).thenReturn("refresh_token=renewed; Path=/api/v1/auth");

        mockMvc.perform(post("/users/me/2fa/disable")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpCodeBody("123456"))))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", "refresh_token=renewed; Path=/api/v1/auth"));
    }

    @Test
    @WithMockUser
    @DisplayName("POST /users/me/2fa/confirm with a blank code fails validation")
    void shouldReturn400ForBlankCode() throws Exception {
        mockMvc.perform(post("/users/me/2fa/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TotpCodeBody(""))))
                .andExpect(status().isBadRequest());
    }

    private record TotpCodeBody(String code) {}
}
