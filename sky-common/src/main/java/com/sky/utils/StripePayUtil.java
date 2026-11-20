package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.StripeApiException;
import com.sky.properties.StripeProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StripePayUtil {

    private static final long WEBHOOK_TOLERANCE_SECONDS = 300;
    private static final RequestConfig TIMEOUTS = RequestConfig.custom()
            .setConnectTimeout(5_000)
            .setConnectionRequestTimeout(5_000)
            .setSocketTimeout(15_000)
            .build();

    @Autowired
    private StripeProperties stripeProperties;

    private final CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(TIMEOUTS).build();

    /**
     * While Stripe is down, every call would wait for a timeout and fail anyway. After at least
     * 5 of the last 10 calls failed on Stripe's side (network, 429, 5xx), the breaker opens and
     * calls fail at once for 30 seconds; then 2 trial calls decide whether it closes again.
     * Requests Stripe rejects as invalid (other 4xx) are the caller's fault and do not count.
     */
    private final CircuitBreaker circuitBreaker = CircuitBreaker.of("stripe", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(10)
            .minimumNumberOfCalls(5)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(2)
            .recordException(e -> e instanceof StripeApiException && ((StripeApiException) e).isRetryable())
            .build());

    public StripePayUtil() {
        circuitBreaker.getEventPublisher().onStateTransition(
                event -> log.warn("Stripe circuit breaker: {}", event.getStateTransition()));
    }

    /**
     * Creates a Stripe Checkout session for one order. The order number travels in the metadata,
     * so the checkout.session.completed webhook can find the order again.
     */
    public JSONObject createCheckoutSession(String orderNumber, BigDecimal amount, String description) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("mode", "payment");
        params.put("success_url", stripeProperties.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}");
        params.put("cancel_url", stripeProperties.getCancelUrl());
        params.put("line_items[0][quantity]", "1");
        params.put("line_items[0][price_data][currency]", "usd");
        params.put("line_items[0][price_data][unit_amount]", String.valueOf(toCents(amount)));
        params.put("line_items[0][price_data][product_data][name]", description);
        params.put("metadata[order_number]", orderNumber);
        params.put("payment_intent_data[metadata][order_number]", orderNumber);
        // Asking again for the same order returns the same session instead of creating a second one.
        return post("/checkout/sessions", params, "checkout-" + orderNumber);
    }

    /** Refunds a payment. Repeating the call with the same idempotency key never refunds twice. */
    public JSONObject refund(String paymentIntentId, BigDecimal amount, String idempotencyKey) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("payment_intent", paymentIntentId);
        params.put("amount", String.valueOf(toCents(amount)));
        return post("/refunds", params, idempotencyKey);
    }

    /** Stripe amounts are whole numbers of the smallest currency unit, so $25.00 is 2500. */
    public static long toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private JSONObject post(String path, Map<String, String> params, String idempotencyKey) {
        String secretKey = stripeProperties.getSecretKey();
        if (secretKey == null || secretKey.isEmpty()) {
            throw new StripeApiException(0, "The Stripe secret key is not configured", false);
        }
        try {
            return circuitBreaker.executeSupplier(() -> send(path, params, idempotencyKey, secretKey));
        } catch (CallNotPermittedException open) {
            throw new StripeApiException(0, "Stripe calls are paused after repeated failures (circuit breaker open)", true);
        }
    }

    /** Circuit breaker state, for monitoring: CLOSED, OPEN or HALF_OPEN. */
    public String circuitBreakerState() {
        return circuitBreaker.getState().name();
    }

    /**
     * Stripe's API takes form-encoded parameters and authenticates with the secret key as a
     * Bearer token. Any non-2xx response becomes a StripeApiException carrying Stripe's message.
     */
    private JSONObject send(String path, Map<String, String> params, String idempotencyKey, String secretKey) {
        HttpPost request = new HttpPost(stripeProperties.getApiBase() + path);
        request.setHeader("Authorization", "Bearer " + secretKey);
        request.setHeader("Idempotency-Key", idempotencyKey);
        List<NameValuePair> form = new ArrayList<>();
        for (Map.Entry<String, String> param : params.entrySet()) {
            form.add(new BasicNameValuePair(param.getKey(), param.getValue()));
        }
        request.setEntity(new UrlEncodedFormEntity(form, StandardCharsets.UTF_8));

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            int status = response.getStatusLine().getStatusCode();
            String body = response.getEntity() == null ? "" : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
            if (status >= 200 && status < 300) {
                return JSON.parseObject(body);
            }
            boolean retryable = status == 429 || status >= 500;
            throw new StripeApiException(status, "Stripe returned " + status + ": " + errorMessage(body), retryable);
        } catch (IOException e) {
            // Timeouts end up here. Stripe may still have done the work; a retry with the same
            // Idempotency-Key then returns that result instead of doing it again.
            throw new StripeApiException("Could not reach Stripe: " + e.getMessage(), e);
        }
    }

    private static String errorMessage(String body) {
        try {
            JSONObject error = JSON.parseObject(body).getJSONObject("error");
            if (error != null && error.getString("message") != null) {
                return error.getString("message");
            }
        } catch (RuntimeException notJson) {
            // Fall back to the raw body below.
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    @PreDestroy
    void close() throws IOException {
        httpClient.close();
    }

    /**
     * Checks the Stripe-Signature header ("t=<unix time>,v1=<hex HMAC-SHA256>").
     * Stripe signs "<t>.<raw request body>" with the endpoint's webhook secret.
     * Returns false when no webhook secret is configured, so unsigned calls are never trusted.
     */
    public boolean verifyWebhookSignature(String payload, String sigHeader) {
        String secret = stripeProperties.getWebhookSecret();
        if (secret == null || secret.isEmpty() || payload == null || sigHeader == null) {
            return false;
        }

        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String item : sigHeader.split(",")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = pair[1];
            } else if ("v1".equals(pair[0])) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return false;
        }

        try {
            // Reject old events so a captured request cannot be replayed later.
            long ageSeconds = Math.abs(System.currentTimeMillis() / 1000 - Long.parseLong(timestamp));
            if (ageSeconds > WEBHOOK_TOLERANCE_SECONDS) {
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder expected = new StringBuilder();
            for (byte b : digest) {
                expected.append(String.format("%02x", b));
            }

            byte[] expectedBytes = expected.toString().getBytes(StandardCharsets.UTF_8);
            for (String signature : signatures) {
                // Constant-time comparison, so response timing does not leak how many characters matched.
                if (MessageDigest.isEqual(expectedBytes, signature.getBytes(StandardCharsets.UTF_8))) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.warn("Could not verify the Stripe webhook signature", e);
            return false;
        }
    }
}
