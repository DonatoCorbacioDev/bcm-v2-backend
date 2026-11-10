package com.donatodev.bcm_backend.service;

import static com.donatodev.bcm_backend.support.TestIds.uid;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
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

    /** The organization every request carries (its token's); a test that needs another sets its own. */
    private static final UUID TENANT = uid(1);

    @BeforeEach
    void setup() {
        TenantContext.set(TENANT);
        billingService = service("sk_test_x", "price_x", WEBHOOK_SECRET);
    }

    @AfterEach
    void leaveTenant() {
        TenantContext.clear();
    }

    private static final String WEBHOOK_SECRET = "whsec_test_secret";
    private static final String FRONTEND = "http://localhost:3000";

    @Mock private StripeCheckoutGateway checkoutGateway;
    @Mock private OrganizationService organizationService;
    @Mock private OrganizationRepository organizationRepository;

    private BillingService billingService;

    private BillingService service(String secretKey, String priceId, String webhookSecret) {
        return new BillingService(checkoutGateway, organizationService, organizationRepository,
                secretKey, priceId, webhookSecret, FRONTEND, true);
    }

    private static Organization org(SubscriptionTier tier) {
        return Organization.builder().id(uid(1)).name("Acme").slug("acme").subscriptionTier(tier).build();
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
            + "\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\"}";

    @Nested
    @DisplayName("createProCheckoutUrl")
    @SuppressWarnings("unused")
    class Checkout {

        @Test
        @DisplayName("returns the Stripe URL for a FREE organization, with org id and return URLs")
        void shouldCreateCheckoutForFreeOrg() throws Exception {
            TenantContext.set(uid(1));
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org(SubscriptionTier.FREE)));
            when(checkoutGateway.createSubscriptionCheckout(uid(1), "price_x", null,
                    FRONTEND + "/organization?checkout=success", FRONTEND + "/organization?checkout=cancel"))
                    .thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");

            assertEquals("https://checkout.stripe.com/c/pay/cs_test_1", billingService.createProCheckoutUrl());
        }

        @Test
        @DisplayName("reuses the org's existing Stripe customer when it resubscribes")
        void shouldReuseExistingCustomer() throws Exception {
            TenantContext.set(uid(1));
            Organization returning = org(SubscriptionTier.FREE);
            returning.setStripeCustomerId("cus_returning");
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(returning));
            when(checkoutGateway.createSubscriptionCheckout(uid(1), "price_x", "cus_returning",
                    FRONTEND + "/organization?checkout=success", FRONTEND + "/organization?checkout=cancel"))
                    .thenReturn("https://checkout.stripe.com/c/pay/cs_test_2");

            assertEquals("https://checkout.stripe.com/c/pay/cs_test_2", billingService.createProCheckoutUrl());
        }

        @Test
        @DisplayName("refuses an organization that is already on a paid plan")
        void shouldRefusePaidOrg() throws Exception {
            TenantContext.set(uid(1));
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org(SubscriptionTier.PRO)));

            assertThrows(IllegalArgumentException.class, () -> billingService.createProCheckoutUrl());
            verify(checkoutGateway, never()).createSubscriptionCheckout(any(), any(), any(), any(), any());
        }

        @ParameterizedTest(name = "secret key \"{0}\", price \"{1}\"")
        @CsvSource({"'', price_x", "sk_test_x, ''"})
        @DisplayName("is unavailable (503) when either the API key or the price is not configured")
        void shouldBeUnavailableWithoutKeys(String secretKey, String priceId) {
            BillingService unconfigured = service(secretKey, priceId, WEBHOOK_SECRET);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    unconfigured::createProCheckoutUrl);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }

        @Test
        @DisplayName("maps a Stripe API failure to 502")
        void shouldMapStripeFailureToBadGateway() throws Exception {
            TenantContext.set(uid(1));
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org(SubscriptionTier.FREE)));
            when(checkoutGateway.createSubscriptionCheckout(any(UUID.class), anyString(), any(), anyString(), anyString()))
                    .thenThrow(new ApiConnectionException("network down"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.createProCheckoutUrl());
            assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        }

        @Test
        @DisplayName("refuses (409) while checkout is switched off, without contacting Stripe or reading the org")
        void shouldRefuseWhenCheckoutDisabled() throws Exception {
            TenantContext.set(uid(1));
            BillingService notForSale = new BillingService(checkoutGateway, organizationService, organizationRepository,
                    "sk_test_x", "price_x", WEBHOOK_SECRET, FRONTEND, false);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, notForSale::createProCheckoutUrl);

            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
            verify(checkoutGateway, never()).createSubscriptionCheckout(any(), any(), any(), any(), any());
            verifyNoInteractions(organizationRepository);
        }

        @Test
        @DisplayName("throws when there is no tenant on the request")
        void shouldThrowWithoutTenant() {
            assertThrows(OrganizationNotFoundException.class, () -> billingService.createProCheckoutUrl());
        }
    }

    @Nested
    @DisplayName("createPortalUrl")
    @SuppressWarnings("unused")
    class Portal {

        @Test
        @DisplayName("opens the Customer Portal for the org's Stripe customer, returning to the organization page")
        void shouldOpenPortalForStripeCustomer() throws Exception {
            TenantContext.set(uid(1));
            Organization subscribed = org(SubscriptionTier.PRO);
            subscribed.setStripeCustomerId("cus_1");
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(subscribed));
            when(checkoutGateway.createPortalSession("cus_1", FRONTEND + "/organization"))
                    .thenReturn("https://billing.stripe.com/p/session/test_1");

            assertEquals("https://billing.stripe.com/p/session/test_1", billingService.createPortalUrl());
        }

        @Test
        @DisplayName("refuses an org with no Stripe customer, e.g. one moved to PRO by hand (400)")
        void shouldRefuseOrgWithoutStripeCustomer() throws Exception {
            TenantContext.set(uid(1));
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(org(SubscriptionTier.PRO)));

            assertThrows(IllegalArgumentException.class, () -> billingService.createPortalUrl());
            verify(checkoutGateway, never()).createPortalSession(any(), any());
        }

        @Test
        @DisplayName("is unavailable (503) when Stripe is not configured")
        void shouldBeUnavailableWithoutKey() {
            BillingService unconfigured = service("", "price_x", WEBHOOK_SECRET);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class, unconfigured::createPortalUrl);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        }

        @Test
        @DisplayName("maps a Stripe API failure to 502")
        void shouldMapStripeFailureToBadGateway() throws Exception {
            TenantContext.set(uid(1));
            Organization subscribed = org(SubscriptionTier.PRO);
            subscribed.setStripeCustomerId("cus_1");
            when(organizationRepository.findById(uid(1))).thenReturn(Optional.of(subscribed));
            when(checkoutGateway.createPortalSession(anyString(), anyString()))
                    .thenThrow(new ApiConnectionException("network down"));

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.createPortalUrl());
            assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("handleWebhook")
    @SuppressWarnings("unused")
    class WebhookHandling {

        @ParameterizedTest(name = "payment_status={0}")
        @ValueSource(strings = {"paid", "no_payment_required"})
        @DisplayName("upgrades to PRO on a completed subscription checkout (paid, or free via e.g. a 100% coupon)")
        void shouldUpgradeOnCompletedCheckout(String paymentStatus) throws Exception {
            String payload = event("checkout.session.completed",
                    "{\"mode\":\"subscription\",\"payment_status\":\"" + paymentStatus + "\",\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\"}");
            String signature = sign(payload, WEBHOOK_SECRET);

            billingService.handleWebhook(payload, signature);

            verify(organizationService).changeTier(uid(1), SubscriptionTier.PRO, "stripe");
        }

        @Test
        @DisplayName("links the checkout's Stripe customer to the org before upgrading it")
        void shouldLinkCustomerThenUpgrade() throws Exception {
            String payload = event("checkout.session.completed",
                    "{\"mode\":\"subscription\",\"payment_status\":\"paid\",\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\","
                    + "\"customer\":\"cus_new\"}");
            String signature = sign(payload, WEBHOOK_SECRET);

            billingService.handleWebhook(payload, signature);

            InOrder order = inOrder(organizationService);
            order.verify(organizationService).linkStripeCustomer(uid(1), "cus_new");
            order.verify(organizationService).changeTier(uid(1), SubscriptionTier.PRO, "stripe");
        }

        @Test
        @DisplayName("does not touch the stored Stripe customer when the subscription ends")
        void shouldKeepCustomerOnCancellation() throws Exception {
            String payload = event("customer.subscription.deleted",
                    "{\"object\":\"subscription\",\"customer\":\"cus_1\",\"metadata\":{\"orgId\":\"00000000-0000-0000-0000-000000000001\"}}");
            String signature = sign(payload, WEBHOOK_SECRET);

            billingService.handleWebhook(payload, signature);

            verify(organizationService).changeTier(uid(1), SubscriptionTier.FREE, "stripe");
            verify(organizationService, never()).linkStripeCustomer(any(), any());
        }

        @Test
        @DisplayName("rejects a malformed body (400) before any signature work or tier change")
        void shouldRejectMalformedPayload() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.handleWebhook("not json {", "t=1,v1=abc"));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', value = {
            "checkout not paid yet | checkout.session.completed | {\"mode\":\"subscription\",\"payment_status\":\"unpaid\",\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\"}",
            "one-off payment, not a subscription | checkout.session.completed | {\"mode\":\"payment\",\"payment_status\":\"paid\",\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\"}",
            "org id not a UUID | checkout.session.completed | {\"mode\":\"subscription\",\"payment_status\":\"paid\",\"client_reference_id\":\"abc\"}",
            "org id not a string | customer.subscription.deleted | {\"object\":\"subscription\",\"metadata\":{\"orgId\":7}}",
            "no org id at all | customer.subscription.deleted | {\"object\":\"subscription\",\"metadata\":{}}",
            "event type not handled | invoice.paid | {\"object\":\"invoice\"}",
        })
        @DisplayName("acknowledges without changing any tier")
        void shouldAcknowledgeWithoutChangingTier(String scenario, String type, String object) throws Exception {
            String payload = event(type, object);
            String signature = sign(payload, WEBHOOK_SECRET);

            billingService.handleWebhook(payload, signature);

            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("downgrades to FREE when the subscription is deleted, using its metadata org id")
        void shouldDowngradeOnSubscriptionDeleted() throws Exception {
            String payload = event("customer.subscription.deleted",
                    "{\"object\":\"subscription\",\"metadata\":{\"orgId\":\"00000000-0000-0000-0000-000000000001\"}}");

            billingService.handleWebhook(payload, sign(payload, WEBHOOK_SECRET));

            verify(organizationService).changeTier(uid(1), SubscriptionTier.FREE, "stripe");
        }

        @Test
        @DisplayName("rejects a payload signed with a different secret (400) and changes nothing")
        void shouldRejectWrongSignature() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);
            String forged = sign(payload, "whsec_attacker");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.handleWebhook(payload, forged));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @Test
        @DisplayName("rejects a payload altered after signing")
        void shouldRejectTamperedPayload() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);
            String signature = sign(payload, WEBHOOK_SECRET);
            String tampered = payload.replace("\"client_reference_id\":\"00000000-0000-0000-0000-000000000001\"", "\"client_reference_id\":\"00000000-0000-0000-0000-000000000002\"");

            assertThrows(ResponseStatusException.class, () -> billingService.handleWebhook(tampered, signature));
            verify(organizationService, never()).changeTier(any(), any(), any());
        }

        @ParameterizedTest(name = "header \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("rejects a missing or blank signature header (400)")
        void shouldRejectMissingSignature(String header) {
            String payload = event("checkout.session.completed", PAID_SESSION);

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> billingService.handleWebhook(payload, header));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("is disabled (404) when no webhook secret is configured")
        void shouldBeDisabledWithoutSecret() {
            BillingService noWebhookSecret = service("sk_test_x", "price_x", "");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> noWebhookSecret.handleWebhook("{}", "t=1,v1=x"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("acknowledges an event for an unknown organization instead of failing (no endless retries)")
        void shouldAcknowledgeUnknownOrg() throws Exception {
            String payload = event("checkout.session.completed", PAID_SESSION);
            String signature = sign(payload, WEBHOOK_SECRET);
            when(organizationService.changeTier(uid(1), SubscriptionTier.PRO, "stripe"))
                    .thenThrow(new OrganizationNotFoundException("Organizzazione non trovata"));

            assertDoesNotThrow(() -> billingService.handleWebhook(payload, signature));
        }
    }
}
