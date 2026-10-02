package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StripePayUtil {

    private static final String STRIPE_API = "https://api.stripe.com/v1";
    private static final long WEBHOOK_TOLERANCE_SECONDS = 300;

    @Autowired
    private StripeProperties stripeProperties;

    public JSONObject createCheckoutSession(String orderNumber, BigDecimal amount, String description) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("success_url", stripeProperties.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}");
            paramMap.put("cancel_url", stripeProperties.getCancelUrl());
            paramMap.put("mode", "payment");


            paramMap.put("line_items[0][quantity]", "1");
            paramMap.put("line_items[0][price_data][currency]", "usd");
            paramMap.put("line_items[0][price_data][product_data][name]", description);
            paramMap.put("line_items[0][price_data][unit_amount]", String.valueOf(amount.multiply(new BigDecimal(100)).longValue()));
            paramMap.put("line_items[0][name]", description);


            paramMap.put("metadata[order_number]", orderNumber);

            String response = HttpClientUtil.doPost4Json(
                    STRIPE_API + "/checkout/sessions", paramMap);

            JSONObject jsonObject = JSON.parseObject(response);
            log.info("Application event: {}", jsonObject.getString("id"));

            return jsonObject;
        } catch (Exception e) {
            log.error("Application event: {}", e);
            throw new RuntimeException("Operation", e);
        }
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

    public String parseOrderNumberFromWebhook(String payload) {
        JSONObject event = JSON.parseObject(payload);


        String eventType = event.getString("type");
        log.info("Application event: {}", eventType);

        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");


        JSONObject metadata = object.getJSONObject("metadata");
        if (metadata != null) {
            return metadata.getString("order_number");
        }


        String paymentIntent = object.getString("payment_intent");
        if (paymentIntent != null) {
            return paymentIntent;
        }

        return null;
    }

    public JSONObject refund(String paymentIntentId, BigDecimal amount) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("payment_intent", paymentIntentId);
            paramMap.put("amount", String.valueOf(amount.multiply(new BigDecimal(100)).longValue()));

            String response = HttpClientUtil.doPost4Json(
                    STRIPE_API + "/refunds", paramMap);

            JSONObject jsonObject = JSON.parseObject(response);
            log.info("Application event: {}", jsonObject.getString("id"));

            return jsonObject;
        } catch (Exception e) {
            log.error("Application event: {}", e);
            throw new RuntimeException("Operation", e);
        }
    }
}