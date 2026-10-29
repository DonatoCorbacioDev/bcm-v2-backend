package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

/**
 * Unit tests for {@link EmailService}. Uses a real {@link MimeMessage} (not a
 * mock) so the sender, recipients and the plain-text/HTML parts actually
 * written into the message can be asserted.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Test: EmailService")
class EmailServiceTest {

    private static final String RECIPIENT = "mario.rossi@studio-rossi.it";

    @Mock
    private JavaMailSender mailSender;

    private EmailService emailService;
    private MimeMessage message;

    @BeforeEach
    void setup() {
        emailService = new EmailService(mailSender, "", "bcm.sender@gmail.com");
        message = new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    @DisplayName("falls back to the SMTP username as sender when app.mail.from is not set")
    void shouldUseSmtpUsernameWhenFromIsMissing() throws Exception {
        EmailService withoutFrom = new EmailService(mailSender, null, "smtp-user@bcm.test");
        when(mailSender.createMimeMessage()).thenReturn(message);

        withoutFrom.sendVerificationEmail(RECIPIENT, "http://verify-link");

        assertThat(message.getFrom()[0].toString()).contains("smtp-user@bcm.test");
    }

    @Test
    @DisplayName("sends the verification email")
    void shouldSendVerificationEmail() {
        when(mailSender.createMimeMessage()).thenReturn(message);

        assertDoesNotThrow(() -> emailService.sendVerificationEmail(RECIPIENT, "http://verify-link"));

        verify(mailSender).send(message);
    }

    @Test
    @DisplayName("sends the reset password email")
    void shouldSendResetPasswordEmail() {
        when(mailSender.createMimeMessage()).thenReturn(message);

        assertDoesNotThrow(() -> emailService.sendResetPasswordEmail(RECIPIENT, "http://reset-link"));

        verify(mailSender).send(message);
    }

    @Test
    @DisplayName("sends the registration code, saying what typing it signs")
    void shouldSendRegistrationCode() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendRegistrationCode(RECIPIENT, "042917", 15);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).contains("042917");
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        String raw = out.toString(StandardCharsets.UTF_8);
        assertThat(raw).contains("042917").contains("15 minuti").contains("1341");
    }

    /** The decoded HTML part, i.e. what a mail client renders. */
    private String htmlPart() throws Exception {
        message.saveChanges();
        return htmlIn(message);
    }

    private static String htmlIn(jakarta.mail.Part part) throws Exception {
        if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        }
        if (part.getContent() instanceof jakarta.mail.Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                String html = htmlIn(multipart.getBodyPart(i));
                if (html != null) {
                    return html;
                }
            }
        }
        return null;
    }

    @Test
    @DisplayName("sends the deletion code, naming the organization with its HTML escaped")
    void shouldSendOrganizationDeletionCode() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendOrganizationDeletionCode(RECIPIENT, "Acme <b>Srl</b>", "318204", 15);

        verify(mailSender).send(message);
        assertThat(message.getSubject()).contains("318204");
        assertThat(htmlPart()).contains("318204").contains("15 minuti").contains("30 giorni")
                .contains("Acme &lt;b&gt;Srl&lt;/b&gt;").doesNotContain("<b>Srl</b>");
    }

    @Test
    @DisplayName("announces a scheduled deletion with its date, keeping the subject on one line")
    void shouldSendOrganizationDeletionScheduled() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendOrganizationDeletionScheduled(RECIPIENT, "Acme\r\nBcc: victim@example.org", "admin",
                java.time.LocalDate.of(2026, java.time.Month.NOVEMBER, 28));

        assertThat(message.getSubject()).doesNotContain("\r", "\n").contains("Acme Bcc: victim@example.org");
        assertThat(message.getHeader("Bcc")).isNull();
        assertThat(htmlPart()).contains("28/11/2026").contains("admin").contains("35 giorni");
    }

    @Test
    @DisplayName("announces a cancelled deletion")
    void shouldSendOrganizationDeletionCancelled() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendOrganizationDeletionCancelled(RECIPIENT, "Acme", "other-admin");

        assertThat(message.getSubject()).contains("annullata");
        assertThat(htmlPart()).contains("other-admin").contains("Nessun dato verrà cancellato");
    }

    @Test
    @DisplayName("wraps a registration code email failure")
    void shouldThrowExceptionOnRegistrationCodeEmailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        doAnswer(invocation -> {
            throw new MessagingException("boom");
        }).when(mailSender).send(message);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendRegistrationCode(RECIPIENT, "000000", 15));

        assertTrue(ex.getMessage().contains("Error sending registration code email"));
    }

    @Test
    @DisplayName("wraps a verification email failure")
    void shouldThrowExceptionOnVerificationEmailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        doAnswer(invocation -> {
            throw new MessagingException("boom");
        }).when(mailSender).send(message);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendVerificationEmail(RECIPIENT, "http://fail"));

        assertTrue(ex.getMessage().contains("Error sending verification email"));
    }

    @Test
    @DisplayName("wraps a reset password email failure")
    void shouldThrowExceptionOnResetPasswordEmailFailure() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        doAnswer(invocation -> {
            throw new MessagingException("boom");
        }).when(mailSender).send(message);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendResetPasswordEmail(RECIPIENT, "http://fail"));

        assertTrue(ex.getMessage().contains("Error sending reset password email"));
    }

    @Test
    @DisplayName("sends a generic email with a named sender and both a plain-text and an HTML part")
    void shouldSendGenericEmailWithNamedSenderAndTextAlternative() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        emailService.sendEmail(RECIPIENT, "[BCM] Oggetto", "<h1>Titolo</h1><p>Corpo &amp; dettagli</p>");

        verify(mailSender).send(message);
        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("bcm.sender@gmail.com");
        assertThat(from.getPersonal()).isEqualTo(EmailService.SENDER_NAME);
        message.saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.writeTo(out);
        String raw = out.toString(StandardCharsets.UTF_8);
        assertThat(raw).contains("multipart/alternative").contains("text/plain").contains("text/html")
                .contains("Corpo & dettagli");
    }

    @Test
    @DisplayName("uses MAIL_FROM as sender when set, not the SMTP login (Brevo-style login ids)")
    void shouldPreferConfiguredFromOverSmtpLogin() throws Exception {
        EmailService brevo = new EmailService(mailSender, " notifiche@donatocorbacio.dev ", "8a1b2c001@smtp-brevo.com");
        when(mailSender.createMimeMessage()).thenReturn(message);

        brevo.sendEmail(RECIPIENT, "Oggetto", "<p>Corpo</p>");

        InternetAddress from = (InternetAddress) message.getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("notifiche@donatocorbacio.dev");
        assertThat(from.getPersonal()).isEqualTo(EmailService.SENDER_NAME);
    }

    @Test
    @DisplayName("leaves the sender to the mail server's default when no account is configured")
    void shouldOmitSenderWhenNotConfigured() throws Exception {
        EmailService noSender = new EmailService(mailSender, "", "");
        when(mailSender.createMimeMessage()).thenReturn(message);

        noSender.sendEmail(RECIPIENT, "Oggetto", "<p>Corpo</p>");

        verify(mailSender).send(message);
        assertThat(message.getFrom()).isNull();
    }

    @Test
    @DisplayName("wraps a generic email failure")
    void shouldThrowExceptionWhenSendGenericEmailFails() {
        when(mailSender.createMimeMessage()).thenReturn(message);
        doAnswer(invocation -> {
            throw new MessagingException("Mail server error");
        }).when(mailSender).send(message);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> emailService.sendEmail(RECIPIENT, "Oggetto", "Corpo"));

        assertTrue(ex.getMessage().contains("Error sending email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin@example.com", "x@mail.example.org", "dev@bcm.test", "a@host.invalid",
            "root@localhost", "no-at-sign", "trailing@"})
    @DisplayName("skips addresses that can never receive mail, without contacting the mail server")
    void shouldSkipUndeliverableAddresses(String address) {
        emailService.sendEmail(address, "Oggetto", "Corpo");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("treats real domains as deliverable, including ones that merely contain 'example'")
    void shouldTreatRealDomainsAsDeliverable() {
        assertTrue(EmailService.isDeliverable("donatocorbacio92@gmail.com"));
        assertTrue(EmailService.isDeliverable("info@myexample.com"));
        assertFalse(EmailService.isDeliverable(null));
    }

    @Test
    @DisplayName("renders readable plain text from the generated HTML")
    void shouldRenderPlainText() {
        String html = "<!DOCTYPE html><html><head><style>body{color:red}</style></head><body>"
                + "<h1>Riepilogo</h1><table><tr><td>NRT-1</td><td>Rossi &amp; C.</td></tr></table>"
                + "<p>Scade il 31/10</p></body></html>";

        String text = EmailService.toPlainText(html);

        assertThat(text).doesNotContain("<").doesNotContain("color:red")
                .contains("Riepilogo").contains("NRT-1").contains("Rossi & C.").contains("Scade il 31/10");
    }

    @Test
    @DisplayName("collapses any run of blank lines between paragraphs into exactly one")
    void shouldCollapseBlankLines() {
        assertThat(EmailService.toPlainText("<p>Primo</p><p></p><p> </p><br><p>Secondo</p>"))
                .isEqualTo("Primo\n\nSecondo");
    }
}
