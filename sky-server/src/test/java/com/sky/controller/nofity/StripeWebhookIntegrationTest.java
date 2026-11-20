package com.sky.controller.nofity;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.support.MySqlIntegrationTest;
import com.sky.support.StripeEventSimulator;
import com.sky.support.TestOrders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.UUID;

import static com.sky.support.StripeEventSimulator.checkoutCompleted;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sends simulated Stripe deliveries through HTTP to the real controller and database: the repeats,
 * tampering and malformed payloads a webhook endpoint meets in production.
 */
@TestPropertySource(properties = "sky.stripe.webhook-secret=" + StripeWebhookIntegrationTest.WEBHOOK_SECRET)
class StripeWebhookIntegrationTest extends MySqlIntegrationTest {

    // Test-only value; real secrets come from configuration and are never committed.
    static final String WEBHOOK_SECRET = "test-webhook-secret-for-integration-tests";

    private final StripeEventSimulator stripe = new StripeEventSimulator(WEBHOOK_SECRET);

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void theSameEventDeliveredThreeTimesPaysTheOrderOnce() {
        Orders order = pendingOrder();
        String eventId = newEventId();
        String event = checkoutCompleted(eventId, order.getNumber(), 2500, "pi_test_three_times");

        ResponseEntity<String> first = deliver(event, stripe.signatureHeader(event));
        ResponseEntity<String> second = deliver(event, stripe.signatureHeader(event));
        ResponseEntity<String> third = deliver(event, stripe.signatureHeader(event));

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertTrue(first.getBody().contains("processed"), first.getBody());
        assertTrue(second.getBody().contains("duplicate"), second.getBody());
        assertTrue(third.getBody().contains("duplicate"), third.getBody());
        Orders paid = orderMapper.getById(order.getId());
        assertEquals(Orders.TO_BE_CONFIRMED, paid.getStatus());
        assertEquals(Orders.PAID, paid.getPayStatus());
        assertEquals("pi_test_three_times", paid.getPaymentIntentId());
        assertEquals("PROCESSED", storedEvent(eventId).get("status"));
        assertEquals(1, count("select count(*) from webhook_event where event_id = ?", eventId));
    }

    @Test
    void aTamperedBodyIsRefusedAndNothingIsStored() {
        Orders order = pendingOrder();
        String eventId = newEventId();
        String signed = checkoutCompleted(eventId, order.getNumber(), 2500, "pi_test_tampered");
        String tampered = signed.replace("\"amount_total\":2500", "\"amount_total\":1");

        ResponseEntity<String> response = deliver(tampered, stripe.signatureHeader(signed));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Orders.PENDING_PAYMENT, orderMapper.getById(order.getId()).getStatus());
        assertEquals(0, count("select count(*) from webhook_event where event_id = ?", eventId));
    }

    @Test
    void aCapturedEventReplayedTenMinutesLaterIsRefused() {
        Orders order = pendingOrder();
        String event = checkoutCompleted(newEventId(), order.getNumber(), 2500, "pi_test_replayed");
        long tenMinutesAgo = System.currentTimeMillis() / 1000 - 600;

        ResponseEntity<String> response = deliver(event, stripe.signatureHeader(event, tenMinutesAgo));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(Orders.PENDING_PAYMENT, orderMapper.getById(order.getId()).getStatus());
    }

    @Test
    void aSignedEventWithTheWrongAmountIsKeptAsRejected() {
        Orders order = pendingOrder();
        String eventId = newEventId();
        String event = checkoutCompleted(eventId, order.getNumber(), 100, "pi_test_wrong_amount");

        ResponseEntity<String> response = deliver(event, stripe.signatureHeader(event));

        // 200: Stripe would only send the same wrong amount again. The row is there for review.
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Orders.PENDING_PAYMENT, orderMapper.getById(order.getId()).getStatus());
        Map<String, Object> stored = storedEvent(eventId);
        assertEquals("REJECTED", stored.get("status"));
        assertTrue(String.valueOf(stored.get("error")).contains("amount_total is 100"), String.valueOf(stored.get("error")));
    }

    @Test
    void aSignedEventWithAnUnexpectedShapeIsKeptAsRejected() {
        String eventId = newEventId();
        String event = "{\"id\":\"" + eventId + "\",\"type\":\"checkout.session.completed\",\"data\":{\"object\":\"oops\"}}";

        ResponseEntity<String> response = deliver(event, stripe.signatureHeader(event));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("REJECTED", storedEvent(eventId).get("status"));
    }

    @Test
    void anEventForAnOrderThatDoesNotExistIsKeptAsRejected() {
        String eventId = newEventId();
        String event = checkoutCompleted(eventId, "no-such-order", 2500, "pi_test_unknown_order");

        assertEquals(HttpStatus.OK, deliver(event, stripe.signatureHeader(event)).getStatusCode());
        assertEquals("REJECTED", storedEvent(eventId).get("status"));
    }

    @Test
    void aPaymentThatArrivesAfterTheTimeoutCancelledTheOrderIsFlagged() {
        Orders cancelled = TestOrders.order(700_001L, Orders.CANCELLED, Orders.UN_PAID);
        orderMapper.insert(cancelled);
        String eventId = newEventId();
        String event = checkoutCompleted(eventId, cancelled.getNumber(), 2500, "pi_test_late");

        assertEquals(HttpStatus.OK, deliver(event, stripe.signatureHeader(event)).getStatusCode());

        assertEquals(Orders.CANCELLED, orderMapper.getById(cancelled.getId()).getStatus());
        Map<String, Object> stored = storedEvent(eventId);
        assertEquals("REJECTED", stored.get("status"));
        assertTrue(String.valueOf(stored.get("error")).contains("must be refunded"));
    }

    private ResponseEntity<String> deliver(String payload, String signature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Stripe-Signature", signature);
        return rest.postForEntity("/notify/stripe", new HttpEntity<>(payload, headers), String.class);
    }

    private Orders pendingOrder() {
        Orders order = TestOrders.order(700_000L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        orderMapper.insert(order);
        return order;
    }

    private static String newEventId() {
        return "evt_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    private Map<String, Object> storedEvent(String eventId) {
        return jdbcTemplate.queryForMap("select status, error, attempts from webhook_event where event_id = ?", eventId);
    }

    private int count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arg);
    }
}
