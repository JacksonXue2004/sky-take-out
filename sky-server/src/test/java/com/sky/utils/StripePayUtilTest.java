package com.sky.utils;

import com.alibaba.fastjson.JSONObject;
import com.sky.exception.StripeApiException;
import com.sky.properties.StripeProperties;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripePayUtilTest {

    // Test-only values; real secrets come from configuration and are never committed.
    private static final String SECRET = "test-webhook-secret";
    private static final String API_KEY = "test-api-key";
    private static final String PAYLOAD = "{\"type\":\"checkout.session.completed\"}";

    private StripeProperties properties;
    private StripePayUtil stripePayUtil;

    @BeforeEach
    void setUp() {
        properties = new StripeProperties();
        properties.setWebhookSecret(SECRET);
        stripePayUtil = new StripePayUtil();
        ReflectionTestUtils.setField(stripePayUtil, "stripeProperties", properties);
    }

    // ---------- webhook signatures ----------

    @Test
    void acceptsAFreshCorrectlySignedEvent() throws Exception {
        long now = nowSeconds();
        assertTrue(stripePayUtil.verifyWebhookSignature(PAYLOAD, header(now, sign(now, PAYLOAD, SECRET))));
    }

    @Test
    void rejectsATamperedPayload() throws Exception {
        long now = nowSeconds();
        String header = header(now, sign(now, PAYLOAD, SECRET));
        assertFalse(stripePayUtil.verifyWebhookSignature(PAYLOAD.replace("completed", "expired"), header));
    }

    @Test
    void rejectsASignatureMadeWithAnotherSecret() throws Exception {
        long now = nowSeconds();
        assertFalse(stripePayUtil.verifyWebhookSignature(PAYLOAD, header(now, sign(now, PAYLOAD, "other-secret"))));
    }

    @Test
    void rejectsAnOldEventToPreventReplay() throws Exception {
        long tenMinutesAgo = nowSeconds() - 600;
        String header = header(tenMinutesAgo, sign(tenMinutesAgo, PAYLOAD, SECRET));
        assertFalse(stripePayUtil.verifyWebhookSignature(PAYLOAD, header));
    }

    @Test
    void rejectsEverythingWhenTheHeaderOrSecretIsMissing() throws Exception {
        long now = nowSeconds();
        String header = header(now, sign(now, PAYLOAD, SECRET));
        assertFalse(stripePayUtil.verifyWebhookSignature(PAYLOAD, null));

        properties.setWebhookSecret("");
        assertFalse(stripePayUtil.verifyWebhookSignature(PAYLOAD, header));
    }

    // ---------- API calls, sent to a local stand-in for api.stripe.com ----------

    @Test
    void checkoutSessionIsAFormPostAuthenticatedWithTheSecretKey() throws Exception {
        FakeStripe stripe = FakeStripe.start(200,
                "{\"id\":\"cs_test_1\",\"url\":\"https://checkout.stripe.com/c/pay/cs_test_1\"}");
        try {
            useApi(stripe);

            JSONObject session = stripePayUtil.createCheckoutSession("abc123", new BigDecimal("25.00"), "Food Order #7");

            assertEquals("cs_test_1", session.getString("id"));
            assertEquals("POST", stripe.method);
            assertEquals("/v1/checkout/sessions", stripe.path);
            assertEquals("Bearer " + API_KEY, stripe.headers.getFirst("Authorization"));
            assertTrue(stripe.headers.getFirst("Content-Type").startsWith("application/x-www-form-urlencoded"));
            assertEquals("checkout-abc123", stripe.headers.getFirst("Idempotency-Key"));
            Map<String, String> form = stripe.form();
            assertEquals("payment", form.get("mode"));
            assertEquals("2500", form.get("line_items[0][price_data][unit_amount]"));
            assertEquals("usd", form.get("line_items[0][price_data][currency]"));
            assertEquals("abc123", form.get("metadata[order_number]"));
        } finally {
            stripe.stop();
        }
    }

    @Test
    void refundNamesThePaymentIntentAndTheAmountInCents() throws Exception {
        FakeStripe stripe = FakeStripe.start(200, "{\"id\":\"re_test_1\",\"status\":\"succeeded\"}");
        try {
            useApi(stripe);

            JSONObject refund = stripePayUtil.refund("pi_test_1", new BigDecimal("19.99"), "refund-abc123");

            assertEquals("re_test_1", refund.getString("id"));
            assertEquals("/v1/refunds", stripe.path);
            assertEquals("refund-abc123", stripe.headers.getFirst("Idempotency-Key"));
            assertEquals("pi_test_1", stripe.form().get("payment_intent"));
            assertEquals("1999", stripe.form().get("amount"));
        } finally {
            stripe.stop();
        }
    }

    @Test
    void aRejectedRequestIsNotRetryableAndCarriesStripesMessage() throws Exception {
        FakeStripe stripe = FakeStripe.start(400, "{\"error\":{\"message\":\"No such payment_intent: pi_missing\"}}");
        try {
            useApi(stripe);

            StripeApiException e = assertThrows(StripeApiException.class,
                    () -> stripePayUtil.refund("pi_missing", new BigDecimal("5.00"), "refund-x"));
            assertEquals(400, e.getStatus());
            assertFalse(e.isRetryable());
            assertTrue(e.getMessage().contains("No such payment_intent"), e.getMessage());
        } finally {
            stripe.stop();
        }
    }

    @Test
    void serverErrorsAndRateLimitsAreRetryable() throws Exception {
        for (int status : new int[]{429, 500, 503}) {
            FakeStripe stripe = FakeStripe.start(status, "{\"error\":{\"message\":\"try again later\"}}");
            try {
                useApi(stripe);
                StripeApiException e = assertThrows(StripeApiException.class,
                        () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
                assertTrue(e.isRetryable(), "status " + status);
            } finally {
                stripe.stop();
            }
        }
    }

    @Test
    void anUnreachableStripeIsRetryable() {
        // Nothing listens on port 1.
        properties.setApiBase("http://localhost:1/v1");
        properties.setSecretKey(API_KEY);

        StripeApiException e = assertThrows(StripeApiException.class,
                () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
        assertTrue(e.isRetryable());
        assertEquals(0, e.getStatus());
    }

    @Test
    void repeatedStripeFailuresOpenTheCircuitSoLaterCallsFailWithoutWaiting() throws Exception {
        FakeStripe stripe = FakeStripe.start(503, "{\"error\":{\"message\":\"unavailable\"}}");
        try {
            useApi(stripe);
            for (int i = 0; i < 5; i++) {
                assertThrows(StripeApiException.class,
                        () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
            }
            assertEquals("OPEN", stripePayUtil.circuitBreakerState());

            StripeApiException e = assertThrows(StripeApiException.class,
                    () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
            assertTrue(e.getMessage().contains("circuit breaker open"), e.getMessage());
            assertTrue(e.isRetryable());
            // The sixth call never reached Stripe.
            assertEquals(5, stripe.requests.get());
        } finally {
            stripe.stop();
        }
    }

    @Test
    void requestsStripeRejectsAsInvalidDoNotOpenTheCircuit() throws Exception {
        FakeStripe stripe = FakeStripe.start(400, "{\"error\":{\"message\":\"bad request\"}}");
        try {
            useApi(stripe);
            for (int i = 0; i < 8; i++) {
                assertThrows(StripeApiException.class,
                        () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
            }
            assertEquals("CLOSED", stripePayUtil.circuitBreakerState());
            assertEquals(8, stripe.requests.get());
        } finally {
            stripe.stop();
        }
    }

    @Test
    void noRequestIsSentWithoutASecretKey() {
        properties.setSecretKey("");

        StripeApiException e = assertThrows(StripeApiException.class,
                () -> stripePayUtil.refund("pi_test_1", new BigDecimal("5.00"), "refund-x"));
        assertFalse(e.isRetryable());
    }

    @Test
    void amountsAreConvertedToWholeCents() {
        assertEquals(2500, StripePayUtil.toCents(new BigDecimal("25")));
        assertEquals(1999, StripePayUtil.toCents(new BigDecimal("19.99")));
        assertEquals(1000, StripePayUtil.toCents(new BigDecimal("9.995")));
    }

    private void useApi(FakeStripe stripe) {
        properties.setApiBase("http://localhost:" + stripe.port() + "/v1");
        properties.setSecretKey(API_KEY);
    }

    /** Records one request and answers it with a fixed status and body. */
    private static final class FakeStripe {
        private final HttpServer server;
        volatile String method;
        volatile String path;
        volatile Headers headers;
        volatile String body;
        final AtomicInteger requests = new AtomicInteger();

        private FakeStripe(int status, String response) throws IOException {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                requests.incrementAndGet();
                method = exchange.getRequestMethod();
                path = exchange.getRequestURI().getPath();
                headers = exchange.getRequestHeaders();
                body = StreamUtils.copyToString(exchange.getRequestBody(), StandardCharsets.UTF_8);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        static FakeStripe start(int status, String response) throws IOException {
            return new FakeStripe(status, response);
        }

        int port() {
            return server.getAddress().getPort();
        }

        Map<String, String> form() throws IOException {
            Map<String, String> form = new HashMap<>();
            for (String pair : body.split("&")) {
                String[] keyValue = pair.split("=", 2);
                form.put(URLDecoder.decode(keyValue[0], "UTF-8"),
                        keyValue.length > 1 ? URLDecoder.decode(keyValue[1], "UTF-8") : "");
            }
            return form;
        }

        void stop() {
            server.stop(0);
        }
    }

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000;
    }

    private static String header(long timestamp, String signature) {
        return "t=" + timestamp + ",v1=" + signature;
    }

    private static String sign(long timestamp, String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
