package com.sky.utils;

import com.sky.properties.StripeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StripePayUtilTest {

    // Test-only value; real secrets come from configuration and are never committed.
    private static final String SECRET = "test-webhook-secret";
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
