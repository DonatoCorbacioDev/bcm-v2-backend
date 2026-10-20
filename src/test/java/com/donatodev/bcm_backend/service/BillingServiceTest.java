package com.donatodev.bcm_backend.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.donatodev.bcm_backend.config.TenantContext;
import com.donatodev.bcm_backend.entity.Organization;
import com.donatodev.bcm_backend.entity.SubscriptionTier;
import com.donatodev.bcm_backend.exception.OrganizationNotFoundException;
import com.donatodev.bcm_backend.repository.OrganizationRepository;
import com.stripe.exception.ApiConnectionException;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String FRONTEND = "http://localhost:3000";

    @Mock private StripeCheckoutGateway checkoutGateway;
    @Mock private OrganizationService organizationService;
    @Mock private OrganizationRepository organizationRepository;

    private BillingService billingService;

    @BeforeEach
    void setup() {
        billingService = service("sk_test_x", "price_x", WEBHOOK_SECRET);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private BillingService service(String secretKey, String priceId, String webhookSecret) {
        return new BillingService(checkoutGateway, organizationService, organizationRepository,
                secretKey, priceId, webhookSecret, FRONTEND);
    }

    private static Organization org(SubscriptionTier tier) {
        return Organization.builder().id(1L).name("Acme").slug("acme").subscriptionTier(tier).build();
    }

    /** Builds a Stripe-Signature header the same way Stripe does: HMAC-SHA256 over "timestamp.payload". */
    private static String sign(String payload, String secret) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(digest);
    }

    private static String event(String type, String objectJson) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"type\":\"" + type + "\",\"data\":{\"object\":"
                + objectJson + "}}";
    }

    private static final String PAID_SESSION =
            "{\"object\":\"checkout.session\",\"mode\":\"subscription\",\"payment_status\":\"paid\","
            + "\"client_reference_id\":\"1\"}";

    @Nested
    @DisplayName("createProCheckoutUrl")
    @SuppressWarnings("unused")
    class Checkout {

        @Test
        @DisplayName("returns the Stripe URL for a FREE organization, with org id and return URLs")
        void shouldCreateCheckoutForFreeOrg() throws Exception {
            TenantContext.set(1L);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(org(SubscriptionTier.FREE)));
            when(checkoutGateway.createSubscriptionCheckout(1L, "price_x",
                    FRONTEND + "/organization?checkout=success", FRONTEND + "/organization?checkout=cancel"))
                    .thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

            assertEquals("https://checkout.stripe.com/c/pay/cs_test_1", billingService.createProCheckoutUrl());
        }

        @Test
        @DisplayName("refuses an organization that is already on a paid plan")
        void shouldRefusePaidOrg() throws Exception {
            TenantContext.set(1L);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(org(SubscriptionTier.PRO)));

            assertThrows(IllegalArgumentException.class, () -> billingService.createProCheckoutUrl());
            verify(checkoutGateway, never()).createSubscriptionCheckout(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("is unavailable (503) when Stripe is not configured")
        void shouldBeUnavailableWithoutKeys() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service("", "price_x", WEBHOOK_SECRET).createProCheckoutUrl());
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }

        @Test
        @DisplayName("maps a Stripe API failure to 502")
        void shouldMapStripeFailureToBadGateway() throws Exception {
            TenantContext.set(1L);
            when(organizationRepository.findById(1L)).thenReturn(Optional.of(org(SubscriptionTier.FREE)));
            when(checkoutGateway.createSubscriptionCheckout(anyLong(), anyString(), anyString(), anyString()))
                    .thenThrow(new ApiConnectionException("network down"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.createProCheckoutUrl());
            assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        }

        @Test
        @DisplayName("throws when there is no tenant on the request")
        void shouldThrowWithoutTenant() {
            assertThrows(OrganizationNotFoundException.class, () -> billingService.createProCheckoutUrl());
        }
    }

    @Nested
    @DisplayName("handleWebhook")
    @SuppressWarnings("unused")
    class WebhookHandling {

        @Test
        @DisplayName("upgrades to PRO on a paid subscription checkout")
        void shouldUpgradeOnPaidCheckout() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService).changeTier(1L, SubscriptionTier.PRO, "stripe");
        }

        @Test
        @DisplayName("does not upgrade when the checkout is not paid yet")
        void shouldNotUpgradeUnpaidCheckout() throws Exception {
            String payload = event("checkout.session.completed",
                    "{\"mode\":\"subscription\",\"payment_status\":\"unpaid\",\"client_reference_id\":\"1\"}");

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("downgrades to FREE when the subscription is deleted, using its metadata org id")
        void shouldDowngradeOnSubscriptionDeleted() throws Exception {
            String payload = event("customer.subscription.deleted",
                    "{\"object\":\"subscription\",\"metadata\":{\"orgId\":\"1\"}}");

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService).changeTier(1L, SubscriptionTier.FREE, "stripe");
        }

        @Test
        @DisplayName("rejects a payload signed with a different secret (400) and changes nothing")
        void shouldRejectWrongSignature() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.handleWebhook(payload, sign(payload, "whsec_attacker")));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("rejects a payload altered after signing")
        void shouldRejectTamperedPayload() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);
            String signature = sign(payload, WEBHOOK_SECRET);
            String tampered = payload.replace("\"client_reference_id\":\"1\"", "\"client_reference_id\":\"2\"");

            assertThrows(ResponseStatusException.class, () -> billingService.handleWebhook(tampered, signature));
            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("rejects a missing signature header (400)")
        void shouldRejectMissingSignature() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.handleWebhook(event("checkout.session.completed", PAID_SESSION), null));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("is disabled (404) when no webhook secret is configured")
        void shouldBeDisabledWithoutSecret() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> service("sk_test_x", "price_x", "").handleWebhook("{}", "t=1,v1=x"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("acknowledges an event for an unknown organization instead of failing (no endless retries)")
        void shouldAcknowledgeUnknownOrg() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);
            when(organizationService.changeTier(eq(1L), eq(SubscriptionTier.PRO), eq("stripe")))
                    .thenThrow(new OrganizationNotFoundException("Organizzazione non trovata"));

            assertDoesNotThrow(() -> billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET)));
        }

        @Test
        @DisplayName("acknowledges an event with no usable org id without changing anything")
        void shouldIgnoreMissingOrgId() throws Exception {
            String payload = event("customer.subscription.deleted", "{\"object\":\"subscription\",\"metadata\":{}}");

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("ignores event types it does not handle")
        void shouldIgnoreOtherEvents() throws Exception {
            String payload = event("invoice.paid", "{\"object\":\"invoice\"}");

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService, never()).changeTier(any(), any(), any());
        }
    }
}
