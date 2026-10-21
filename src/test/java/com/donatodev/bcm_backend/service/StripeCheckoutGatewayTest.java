package com.donatodev.bcm_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import com.stripe.model.checkout.Session;
import com.stripe.net.BearerTokenAuthenticator;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

/**
 * Asserts what is actually sent to Stripe, without the network: Session.create
 * is static, so it is intercepted and its arguments captured.
 */
class StripeCheckoutGatewayTest {

    @Test
    @DisplayName("creates a subscription session tagged with the org id twice, using a per-request API key")
    void shouldBuildSubscriptionSession() throws Exception {
        Session session = mock(Session.class);
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/c/pay/cs_test_1");
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);
        ArgumentCaptor<RequestOptions> options = ArgumentCaptor.forClass(RequestOptions.class);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(session);

            String url = new StripeCheckoutGateway("sk_test_abc")
                    .createSubscriptionCheckout(42L, "price_pro", null, "http://app/ok", "http://app/ko");

            assertThat(url).isEqualTo("https://checkout.stripe.com/c/pay/cs_test_1");
            stripe.verify(() -> Session.create(params.capture(), options.capture()));
        }

        SessionCreateParams sent = params.getValue();
        assertThat(sent.getMode()).isEqualTo(SessionCreateParams.Mode.SUBSCRIPTION);
        assertThat(sent.getClientReferenceId()).isEqualTo("42");
        assertThat(sent.getSubscriptionData().getMetadata()).containsEntry(BillingService.ORG_ID_METADATA_KEY, "42");
        assertThat(sent.getLineItems()).singleElement().satisfies(item -> {
            assertThat(item.getPrice()).isEqualTo("price_pro");
            assertThat(item.getQuantity()).isEqualTo(1L);
        });
        assertThat(sent.getSuccessUrl()).isEqualTo("http://app/ok");
        assertThat(sent.getCancelUrl()).isEqualTo("http://app/ko");
        assertThat(options.getValue().getAuthenticator())
                .isInstanceOfSatisfying(BearerTokenAuthenticator.class,
                        auth -> assertThat(auth.getApiKey()).isEqualTo("sk_test_abc"));
        assertThat(sent.getCustomer()).isNull();
    }

    @Test
    @DisplayName("reuses an existing Stripe customer instead of letting Checkout create a duplicate")
    void shouldReuseExistingCustomer() throws Exception {
        Session session = mock(Session.class);
        ArgumentCaptor<SessionCreateParams> params = ArgumentCaptor.forClass(SessionCreateParams.class);

        try (MockedStatic<Session> stripe = mockStatic(Session.class)) {
            stripe.when(() -> Session.create(any(SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(session);

            new StripeCheckoutGateway("sk_test_abc")
                    .createSubscriptionCheckout(42L, "price_pro", "cus_existing", "http://app/ok", "http://app/ko");

            stripe.verify(() -> Session.create(params.capture(), any(RequestOptions.class)));
        }

        assertThat(params.getValue().getCustomer()).isEqualTo("cus_existing");
    }

    @Test
    @DisplayName("opens a Customer Portal session for the customer, returning to the given URL")
    void shouldCreatePortalSession() throws Exception {
        com.stripe.model.billingportal.Session portal = mock(com.stripe.model.billingportal.Session.class);
        when(portal.getUrl()).thenReturn("https://billing.stripe.com/p/session/test_1");
        ArgumentCaptor<com.stripe.param.billingportal.SessionCreateParams> params =
                ArgumentCaptor.forClass(com.stripe.param.billingportal.SessionCreateParams.class);

        try (MockedStatic<com.stripe.model.billingportal.Session> stripe =
                mockStatic(com.stripe.model.billingportal.Session.class)) {
            stripe.when(() -> com.stripe.model.billingportal.Session.create(
                    any(com.stripe.param.billingportal.SessionCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(portal);

            String url = new StripeCheckoutGateway("sk_test_abc").createPortalSession("cus_1", "http://app/org");

            assertThat(url).isEqualTo("https://billing.stripe.com/p/session/test_1");
            stripe.verify(() -> com.stripe.model.billingportal.Session.create(
                    params.capture(), any(RequestOptions.class)));
        }

        assertThat(params.getValue().getCustomer()).isEqualTo("cus_1");
        assertThat(params.getValue().getReturnUrl()).isEqualTo("http://app/org");
    }
}
