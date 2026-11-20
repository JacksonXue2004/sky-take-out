package com.sky.support;

import com.alibaba.fastjson.JSONObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;

/**
 * Builds Stripe webhook events and their Stripe-Signature headers the way Stripe does, so tests can
 * replay deliveries, tamper with them, or send them too late.
 */
public final class StripeEventSimulator {

    private final String webhookSecret;

    public StripeEventSimulator(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /** A checkout.session.completed event for a card payment that went through. */
    public static String checkoutCompleted(String eventId, String orderNumber, long amountCents, String paymentIntent) {
        return checkoutEvent(eventId, "checkout.session.completed", orderNumber, amountCents, "usd", "paid", paymentIntent);
    }

    public static String checkoutEvent(String eventId, String type, String orderNumber, long amountCents,
                                       String currency, String paymentStatus, String paymentIntent) {
        JSONObject metadata = new JSONObject(true);
        metadata.put("order_number", orderNumber);

        JSONObject session = new JSONObject(true);
        session.put("id", "cs_test_" + eventId);
        session.put("object", "checkout.session");
        session.put("amount_total", amountCents);
        session.put("currency", currency);
        session.put("payment_status", paymentStatus);
        session.put("payment_intent", paymentIntent);
        session.put("metadata", metadata);

        JSONObject data = new JSONObject(true);
        data.put("object", session);

        JSONObject event = new JSONObject(true);
        event.put("id", eventId);
        event.put("object", "event");
        event.put("type", type);
        event.put("created", System.currentTimeMillis() / 1000);
        event.put("data", data);
        return event.toJSONString();
    }

    /** The Stripe-Signature header for a delivery made now. */
    public String signatureHeader(String payload) {
        return signatureHeader(payload, System.currentTimeMillis() / 1000);
    }

    /** The Stripe-Signature header for a delivery made at the given Unix time. */
    public String signatureHeader(String payload, long timestamp) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return "t=" + timestamp + ",v1=" + hex;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
