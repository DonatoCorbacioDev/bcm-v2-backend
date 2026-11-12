package com.donatodev.bcm_backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.donatodev.bcm_backend.entity.AuditLog;
import com.donatodev.bcm_backend.repository.AuditLogRepository;
import com.donatodev.bcm_backend.service.IEmailService;

/** OWASP API6: the weekly email that shows the operator what was refused, and by whom. */
class SecurityReportServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-16T08:05:00Z");

    private final AuditLogRepository repository = mock(AuditLogRepository.class);
    private final IEmailService email = mock(IEmailService.class);

    private SecurityReportService service(String address) {
        return new SecurityReportService(repository, email, address, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static AuditLog event(String action, String username, String details) {
        return AuditLog.builder().action(action).entityType("Security").username(username).details(details)
                .timestamp(NOW).build();
    }

    @Test
    @DisplayName("no operator address configured: nothing is read or sent")
    void noAddressNoReport() {
        service("  ").sendWeeklyReport();
        service(null).sendWeeklyReport();

        verify(email, never()).sendEmail(anyString(), anyString(), anyString());
        verify(repository, never()).findByActionInAndTimestampAfter(any(), any());
    }

    @Test
    @DisplayName("reads the last seven days of every kind of event, and mails the operator")
    @SuppressWarnings("unchecked")
    void readsTheWeekAndMails() {
        when(repository.findByActionInAndTimestampAfter(any(), any())).thenReturn(List.of(
                event("SECURITY_COST_LIMITED", "demo", "ip=203.0.113.7 | MlProxyController.askAgent")));

        service("ops@example.com").sendWeeklyReport();

        ArgumentCaptor<Collection<String>> actions = ArgumentCaptor.forClass(Collection.class);
        verify(repository).findByActionInAndTimestampAfter(actions.capture(), eq(NOW.minus(SecurityReportService.PERIOD)));
        Set<String> every = Arrays.stream(SecurityEvents.Type.values()).map(SecurityEvents.Type::action)
                .collect(Collectors.toSet());
        assertThat(actions.getValue()).containsExactlyInAnyOrderElementsOf(every);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendEmail(eq("ops@example.com"), eq(SecurityReportService.SUBJECT), body.capture());
        assertThat(body.getValue()).contains("Budget delle operazioni costose superato: <b>1</b>");
    }

    @Test
    @DisplayName("a quiet week still sends a short all-clear")
    void quietWeek() {
        when(repository.findByActionInAndTimestampAfter(any(), any())).thenReturn(List.of());

        service("ops@example.com").sendWeeklyReport();

        verify(email).sendEmail(eq("ops@example.com"), anyString(),
                org.mockito.ArgumentMatchers.contains("Nessun segnale"));
    }

    @Test
    @DisplayName("counts per kind, and the users and addresses behind the most episodes")
    void countsAndTopOffenders() {
        List<AuditLog> events = List.of(
                event("SECURITY_RATE_LIMITED", null, "ip=198.51.100.9 | /auth/login"),
                event("SECURITY_RATE_LIMITED", null, "ip=198.51.100.9 | /auth/login"),
                event("SECURITY_RATE_LIMITED", null, "ip=192.0.2.1 | /auth/login"),
                event("SECURITY_DEMO_BLOCKED", "demo", "ip=203.0.113.7 | UserController.changeOwnPassword"),
                event("SEPA_IBAN_MISMATCH_BLOCKED", "anna", "ip=- | SEPA payment blocked"));

        String body = SecurityReportService.body(events, byAction());

        assertThat(body)
                .contains("Troppi tentativi di accesso, reset o registrazione da un indirizzo: <b>3</b>")
                .contains("Azioni disattivate tentate dalla demo pubblica: <b>1</b>")
                .contains("Pagamenti SEPA bloccati per IBAN cambiato: <b>1</b>")
                .doesNotContain("Pagamenti SEPA duplicati")
                .contains("<li>198.51.100.9: 2</li>")
                .contains("<li>demo: 1</li>");
        // Most episodes first.
        assertThat(body.indexOf("198.51.100.9")).isLessThan(body.indexOf("192.0.2.1"));
    }

    @Test
    @DisplayName("a username is escaped: it is text a visitor chose")
    void escapesUserText() {
        String body = SecurityReportService.body(
                List.of(event("SECURITY_COST_LIMITED", "<img src=x>", "ip=1.2.3.4")), byAction());

        assertThat(body).contains("&lt;img src=x&gt;").doesNotContain("<img");
    }

    @Test
    @DisplayName("the address is read back from the start of the stored text")
    void readsTheAddress() {
        assertThat(SecurityReportService.ipOf("ip=203.0.113.7 | x")).isEqualTo("203.0.113.7");
        assertThat(SecurityReportService.ipOf("ip=203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(SecurityReportService.ipOf("ip=- | x")).isNull();
        assertThat(SecurityReportService.ipOf("ip=")).isNull();
        assertThat(SecurityReportService.ipOf("something else")).isNull();
        assertThat(SecurityReportService.ipOf(null)).isNull();
    }

    private static java.util.Map<String, SecurityEvents.Type> byAction() {
        return Arrays.stream(SecurityEvents.Type.values())
                .collect(Collectors.toMap(SecurityEvents.Type::action, t -> t));
    }
}
