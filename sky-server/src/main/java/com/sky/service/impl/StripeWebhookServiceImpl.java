package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.entity.Orders;
import com.sky.entity.WebhookEvent;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.WebhookEventMapper;
import com.sky.service.OrderService;
import com.sky.service.RefundService;
import com.sky.service.StripeWebhookService;
import com.sky.utils.StripePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Stripe webhook pipeline, after the signature check: record the event, skip repeats,
 * validate it against the order, then mark the order paid.
 * <p>
 * Stripe delivers events at least once and retries until it gets a 2xx, so every event is stored
 * under its unique event id first. A repeat of a finished event is answered without doing anything.
 * Events that are signed but unusable (unknown order, wrong amount, malformed) are stored as REJECTED
 * and answered with 200: retrying would not fix them, and the stored row keeps them for review.
 */
@Service
@Slf4j
public class StripeWebhookServiceImpl implements StripeWebhookService {

    private static final List<String> FINISHED = Arrays.asList(
            WebhookEvent.PROCESSED, WebhookEvent.IGNORED, WebhookEvent.REJECTED);
    private static final int MAX_ERROR_LENGTH = 500;

    @Autowired
    private WebhookEventMapper webhookEventMapper;
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderService orderService;
    @Autowired
    private RefundService refundService;

    @Override
    public Outcome handle(String payload) {
        JSONObject event = parse(payload);
        if (event == null || isBlank(event.getString("id")) || isBlank(event.getString("type"))) {
            store(null, null, payload, WebhookEvent.REJECTED, "Not a Stripe event: the id or type is missing");
            log.warn("Rejected a signed Stripe webhook without an event id or type");
            return Outcome.REJECTED;
        }
        String eventId = event.getString("id");
        String type = event.getString("type");

        WebhookEvent stored;
        try {
            stored = store(eventId, type, payload, WebhookEvent.RECEIVED, null);
        } catch (DuplicateKeyException repeat) {
            stored = webhookEventMapper.getByEventId(eventId);
            if (FINISHED.contains(stored.getStatus())) {
                log.info("Stripe event {} was already handled ({}); ignoring the repeat delivery", eventId, stored.getStatus());
                return Outcome.DUPLICATE;
            }
            // An earlier delivery failed or stopped midway. Handling it again is safe: paySuccess is idempotent.
            webhookEventMapper.countAttempt(stored.getId());
        }

        Result result;
        try {
            result = process(type, event);
        } catch (RuntimeException e) {
            log.error("Failed to process Stripe event {}", eventId, e);
            finish(stored, WebhookEvent.FAILED, null, e.toString());
            return Outcome.FAILED;
        }
        finish(stored, result.status, result.orderNumber, result.error);
        if (WebhookEvent.REJECTED.equals(result.status)) {
            log.warn("Rejected Stripe event {} ({}): {}", eventId, type, result.error);
        }
        return result.outcome;
    }

    private Result process(String type, JSONObject event) {
        if (!"checkout.session.completed".equals(type) && !"checkout.session.async_payment_succeeded".equals(type)) {
            return Result.ignored(null, "This event type is not used");
        }

        CheckoutSession session;
        try {
            session = CheckoutSession.read(event);
        } catch (RuntimeException e) {
            // Missing fields, or fields of the wrong JSON type.
            return Result.rejected(null, "Malformed checkout session: " + e.getMessage());
        }
        if (!"paid".equals(session.paymentStatus)) {
            // Delayed methods such as bank debits are not paid yet; Stripe sends
            // checkout.session.async_payment_succeeded once the money arrives.
            return Result.ignored(session.orderNumber, "payment_status is " + session.paymentStatus);
        }

        Orders order = orderMapper.getByNumber(session.orderNumber);
        if (order == null) {
            return Result.rejected(session.orderNumber, "No order has this number");
        }
        if (!"usd".equalsIgnoreCase(session.currency)) {
            return Result.rejected(session.orderNumber, "Currency is " + session.currency + ", expected usd");
        }
        long expectedCents = StripePayUtil.toCents(order.getAmount());
        if (session.amountTotal == null || session.amountTotal != expectedCents) {
            return Result.rejected(session.orderNumber,
                    "amount_total is " + session.amountTotal + " but the order total is " + expectedCents);
        }

        if (orderService.paySuccess(session.orderNumber, session.paymentIntent)) {
            return Result.processed(session.orderNumber);
        }
        Orders current = orderMapper.getByNumber(session.orderNumber);
        if (session.paymentIntent != null && session.paymentIntent.equals(current.getPaymentIntentId())) {
            return Result.ignored(session.orderNumber, "The order was already marked paid by this payment");
        }
        // Typically the 15-minute payment timeout cancelled the order before the customer finished paying.
        // The money was taken for an order that will not be made, so it goes back.
        if (session.paymentIntent == null) {
            log.error("Order {} was paid at Stripe but is in status {}, and the event names no payment to refund",
                    session.orderNumber, current.getStatus());
            return Result.rejected(session.orderNumber, "The order is in status " + current.getStatus()
                    + " and no longer takes payment; the payment must be refunded by hand");
        }
        refundService.enqueue(current.getId(), current.getNumber(), session.paymentIntent, current.getAmount());
        log.warn("Order {} was paid at Stripe ({}) after it moved to status {}; queued a refund",
                session.orderNumber, session.paymentIntent, current.getStatus());
        return Result.rejected(session.orderNumber, "The order is in status " + current.getStatus()
                + " and no longer takes payment; payment " + session.paymentIntent + " must be refunded (refund queued)");
    }

    private WebhookEvent store(String eventId, String type, String payload, String status, String error) {
        LocalDateTime now = LocalDateTime.now();
        WebhookEvent event = WebhookEvent.builder()
                .eventId(eventId)
                .type(type)
                .status(status)
                .error(truncate(error))
                .payload(payload)
                .attempts(1)
                .receivedAt(now)
                .processedAt(WebhookEvent.RECEIVED.equals(status) ? null : now)
                .build();
        webhookEventMapper.insert(event);
        return event;
    }

    private void finish(WebhookEvent event, String status, String orderNumber, String error) {
        event.setStatus(status);
        event.setOrderNumber(orderNumber);
        event.setError(truncate(error));
        event.setProcessedAt(LocalDateTime.now());
        webhookEventMapper.finish(event);
    }

    private static JSONObject parse(String payload) {
        try {
            return JSON.parseObject(payload);
        } catch (RuntimeException notAJsonObject) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String truncate(String text) {
        return text == null || text.length() <= MAX_ERROR_LENGTH ? text : text.substring(0, MAX_ERROR_LENGTH);
    }

    /** The parts of a Checkout Session that the order needs, read from the event without any database access. */
    private static final class CheckoutSession {
        String orderNumber;
        String paymentStatus;
        String currency;
        Long amountTotal;
        String paymentIntent;

        static CheckoutSession read(JSONObject event) {
            JSONObject data = event.getJSONObject("data");
            JSONObject object = data == null ? null : data.getJSONObject("object");
            if (object == null) {
                throw new IllegalArgumentException("data.object is missing");
            }
            JSONObject metadata = object.getJSONObject("metadata");
            CheckoutSession session = new CheckoutSession();
            session.orderNumber = metadata == null ? null : metadata.getString("order_number");
            if (isBlank(session.orderNumber)) {
                throw new IllegalArgumentException("metadata.order_number is missing");
            }
            session.paymentStatus = object.getString("payment_status");
            session.currency = object.getString("currency");
            session.amountTotal = object.getLong("amount_total");
            session.paymentIntent = object.getString("payment_intent");
            return session;
        }
    }

    private static final class Result {
        final Outcome outcome;
        final String status;
        final String orderNumber;
        final String error;

        private Result(Outcome outcome, String status, String orderNumber, String error) {
            this.outcome = outcome;
            this.status = status;
            this.orderNumber = orderNumber;
            this.error = error;
        }

        static Result processed(String orderNumber) {
            return new Result(Outcome.PROCESSED, WebhookEvent.PROCESSED, orderNumber, null);
        }

        static Result ignored(String orderNumber, String reason) {
            return new Result(Outcome.IGNORED, WebhookEvent.IGNORED, orderNumber, reason);
        }

        static Result rejected(String orderNumber, String reason) {
            return new Result(Outcome.REJECTED, WebhookEvent.REJECTED, orderNumber, reason);
        }
    }
}
