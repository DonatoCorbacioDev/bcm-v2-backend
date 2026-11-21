package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.donatodev.bcm_backend.auth.LoginAttemptMonitor;
import com.donatodev.bcm_backend.auth.LoginLockout;
import com.donatodev.bcm_backend.auth.RefreshCookieFactory;
import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.entity.Managers;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.RefreshToken;
import com.donatodev.bcm_backend.entity.Roles;
import com.donatodev.bcm_backend.entity.Users;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.repository.ManagersRepository;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.donatodev.bcm_backend.repository.RefreshTokenRepository;
import com.donatodev.bcm_backend.repository.RolesRepository;
import com.donatodev.bcm_backend.repository.UsersRepository;
import com.donatodev.bcm_backend.service.IEmailService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * OWASP Authentication and Session Management Cheat Sheet, through the real endpoints: one answer to
 * a wrong name, a wrong password and a locked account; a session with an end
 * however often it is renewed; rotation that never forks a session; logout
 * and replay that end the access token too; the shared demo account that no
 * visitor can lock or log the others out of; and every suspicious sign-in in
 * the audit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "bcm.demo.locked-username=" + AuthenticationCheatSheetTest.DEMO)
class AuthenticationCheatSheetTest {

    static final String DEMO = "jwt_demo_visitor";
    private static final String PASSWORD = "Password1!";
    private static final String GENERIC = "Nome utente o password non validi";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UsersRepository users;
    @Autowired private RolesRepository roles;
    @Autowired private ManagersRepository managers;
    @Autowired private OrganizationRepository organizations;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AuditLogRepository auditLogs;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private IEmailService emailService;

    private Organization org;
    private Roles role;

    @BeforeEach
    void seed() {
        org = organizations.save(Organization.builder().name("JWT").slug("jwt-" + UUID.randomUUID()).build());
        role = roles.findByRole("MANAGER").orElseGet(() -> roles.save(Roles.builder().role("MANAGER").build()));
    }

    @Test
    @DisplayName("a wrong name, a wrong password and a locked account get the same answer")
    void oneAnswerForEveryRefusal() throws Exception {
        Users user = user(name(), PASSWORD);
        Users locked = user(name(), PASSWORD);
        locked.setFailedLoginAttempts(LoginLockout.MAX_FAILED_ATTEMPTS);
        locked.setLockedUntil(java.time.LocalDateTime.now().plusMinutes(10));
        users.save(locked);

        String unknown = refusedLogin("nobody-" + UUID.randomUUID(), PASSWORD);
        String wrong = refusedLogin(user.getUsername(), "Wrong1!");
        // The right password while locked: still the same answer.
        String whileLocked = refusedLogin(locked.getUsername(), PASSWORD);

        assertThat(List.of(unknown, wrong, whileLocked)).allSatisfy(body -> assertThat(body).contains(GENERIC));
        assertThat(unknown).isEqualTo(wrong).isEqualTo(whileLocked);
    }

    @Test
    @DisplayName("the fifth wrong password locks the account, is recorded, and its owner gets an email")
    void lockIsRecordedAndTold() throws Exception {
        Users user = user(name(), PASSWORD);
        for (int i = 0; i < LoginLockout.MAX_FAILED_ATTEMPTS; i++) {
            refusedLogin(user.getUsername(), "Wrong1!");
        }

        assertThat(actions()).contains("SECURITY_LOGIN_FAILED", "SECURITY_ACCOUNT_LOCKED");
        verify(emailService, timeout(5000)).sendEmail(eq(user.getManager().getEmail()),
                eq(LoginAttemptMonitor.LOCKED_SUBJECT), contains(user.getUsername()));
        refusedLogin(user.getUsername(), PASSWORD);
    }

    @Test
    @DisplayName("a name that matches no account leaves no record of what was typed")
    void unknownNameIsNotRecorded() throws Exception {
        String typed = "maybe-a-password-" + UUID.randomUUID();
        refusedLogin(typed, PASSWORD);

        assertThat(auditLogs.findAll()).noneMatch(log -> typed.equals(log.getUsername())
                || (log.getDetails() != null && log.getDetails().contains(typed)));
    }

    @Test
    @DisplayName("a renewed session still ends when the first one would have: the expiry is carried over")
    void sessionHasAnAbsoluteEnd() throws Exception {
        Users user = user(name(), PASSWORD);
        String refresh = refreshCookie(login(user.getUsername()));
        Instant end = activeToken(user).getExpiryDate();

        for (int i = 0; i < 3; i++) {
            refresh = refreshCookie(refresh(refresh).andExpect(status().isOk()).andReturn().getResponse());
        }

        assertThat(activeToken(user).getExpiryDate()).isEqualTo(end);
    }

    @Test
    @DisplayName("a logout ends the access token at once, not 15 minutes later")
    void logoutEndsTheAccessToken() throws Exception {
        Users user = user(name(), PASSWORD);
        MockHttpServletResponse session = login(user.getUsername());
        Thread.sleep(1100); // tokens carry whole seconds

        mvc.perform(post("/auth/logout").cookie(cookie(refreshCookie(session)))).andExpect(status().isNoContent());

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken(session)))
                .andExpect(status().isUnauthorized());
        refresh(refreshCookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a replayed refresh token ends the thief's and the owner's sessions, access tokens included, and is recorded")
    void replayEndsEverything() throws Exception {
        Users user = user(name(), PASSWORD);
        String stolen = refreshCookie(login(user.getUsername()));
        MockHttpServletResponse owner = refresh(stolen).andExpect(status().isOk()).andReturn().getResponse();
        Thread.sleep(1100);

        refresh(stolen).andExpect(status().isUnauthorized());

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken(owner)))
                .andExpect(status().isUnauthorized());
        refresh(refreshCookie(owner)).andExpect(status().isUnauthorized());
        assertThat(actions()).contains("SECURITY_REFRESH_REUSE");
    }

    @Test
    @DisplayName("a device left behind by a new login gets a 401, and does not end the new session")
    void staleDeviceIsNoThief() throws Exception {
        Users user = user(name(), PASSWORD);
        String oldDevice = refreshCookie(login(user.getUsername()));
        MockHttpServletResponse newDevice = login(user.getUsername());

        refresh(oldDevice).andExpect(status().isUnauthorized());

        refresh(refreshCookie(newDevice)).andExpect(status().isOk());
        assertThat(actions()).doesNotContain("SECURITY_REFRESH_REUSE");
    }

    @Test
    @DisplayName("two refreshes with the same token at once never give two live sessions")
    void concurrentRefreshDoesNotFork() throws Exception {
        Users user = user(name(), PASSWORD);
        String refresh = refreshCookie(login(user.getUsername()));
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            return refresh(refresh).andReturn().getResponse().getStatus();
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>(List.of(pool.submit(attempt), pool.submit(attempt)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).containsOnly(200, 401).contains(401);
        } finally {
            pool.shutdownNow();
        }
        assertThat(refreshTokens.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())).hasSizeLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("the shared demo account: no visitor locks it, or logs the others out")
    void demoVisitorsDoNotInterfere() throws Exception {
        user(DEMO, PASSWORD);
        for (int i = 0; i < LoginLockout.MAX_FAILED_ATTEMPTS + 1; i++) {
            refusedLogin(DEMO, "Wrong1!");
        }
        MockHttpServletResponse first = login(DEMO);
        MockHttpServletResponse second = login(DEMO);
        Thread.sleep(1100);

        mvc.perform(post("/auth/logout").cookie(cookie(refreshCookie(first)))).andExpect(status().isNoContent());
        refresh(refreshCookie(first)).andExpect(status().isUnauthorized());

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + accessToken(second))).andExpect(status().isOk());
        refresh(refreshCookie(second)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a forged session token is recorded with its kind of failure, never the token itself")
    void forgedTokenIsRecorded() throws Exception {
        Users user = user(name(), PASSWORD);
        String[] parts = accessToken(login(user.getUsername())).split("\\.");
        String forged = parts[0] + "." + parts[1] + "." + new StringBuilder(parts[2]).reverse();
        String address = "203.0.113.77";

        mvc.perform(get("/auth/me").header("Authorization", "Bearer " + forged)
                .with(request -> { request.setRemoteAddr(address); return request; }))
                .andExpect(status().isUnauthorized());

        List<AuditLog> recorded = auditLogs.findAll().stream()
                .filter(log -> "SECURITY_INVALID_TOKEN".equals(log.getAction())
                        && log.getDetails() != null && log.getDetails().startsWith("ip=" + address))
                .toList();
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getDetails()).contains("GET /auth/me").doesNotContain(parts[2]);
    }

    @Test
    @DisplayName("signing in, a wrong password, a renewal and a password reset never write a password or a token to the log")
    @ExtendWith(OutputCaptureExtension.class)
    void credentialsNeverReachTheLog(CapturedOutput output) throws Exception {
        Users user = user(name(), PASSWORD);
        String wrong = "Typed-By-Mistake-7!";
        String newPassword = "Brand-New-Pass-8!";

        MockHttpServletResponse session = login(user.getUsername());
        refusedLogin(user.getUsername(), wrong);
        String renewed = refreshCookie(refresh(refreshCookie(session)).andExpect(status().isOk()).andReturn().getResponse());

        String email = user.getManager().getEmail();
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService, timeout(5000)).sendResetPasswordEmail(eq(email), link.capture(), any(), any());
        String resetToken = link.getValue().substring(link.getValue().indexOf("token=") + "token=".length());
        mvc.perform(post("/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + resetToken + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk());

        // The capture works: the events themselves are there.
        assertThat(output.getAll()).contains("Sign-in succeeded", "LOGIN_FAILED");
        assertThat(output.getAll()).doesNotContain(PASSWORD, wrong, newPassword, accessToken(session),
                refreshCookie(session), renewed, resetToken);
    }

    // --- helpers ---

    private static String name() {
        return "jwt" + UUID.randomUUID().toString().substring(0, 12);
    }

    private Users user(String username, String password) {
        return users.save(Users.builder().username(username).passwordHash(passwordEncoder.encode(password))
                .verified(true).organization(org).manager(manager(org)).role(role).build());
    }

    private Managers manager(Organization organization) {
        return managers.save(Managers.builder().organization(organization).firstName("Anna").lastName("Verdi")
                .email(UUID.randomUUID() + "@example.com").build());
    }

    private List<String> actions() {
        return auditLogs.findAllByOrgIdOrderByTimestampAsc(org.getId()).stream().map(AuditLog::getAction).toList();
    }

    private RefreshToken activeToken(Users user) {
        return refreshTokens.findAll().stream()
                .filter(t -> t.getUser().getId().equals(user.getId()) && !t.isRevoked())
                .findFirst().orElseThrow();
    }

    private String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private String refusedLogin(String username, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(username, password)))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString()
                .replaceAll("\"timestamp\":\"[^\"]*\"", "");
    }

    private MockHttpServletResponse login(String username) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(username, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/auth/refresh").cookie(cookie(refreshToken)));
    }

    private static Cookie cookie(String value) {
        return new Cookie(RefreshCookieFactory.COOKIE_NAME, value);
    }

    private static String refreshCookie(MockHttpServletResponse response) {
        return response.getCookie(RefreshCookieFactory.COOKIE_NAME).getValue();
    }

    private String accessToken(MockHttpServletResponse response) throws Exception {
        return json.readTree(response.getContentAsString()).get("token").asText();
    }
}
