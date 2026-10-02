package com.sky.controller.nofity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.service.OrderService;
import com.sky.utils.StripePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderService orderService;
    @Autowired
    private StripePayUtil stripePayUtil;

    /**
     * Stripe webhook. The body is read as raw text because the signature covers the exact bytes
     * Stripe sent. An invalid signature gets 400. A processing error gets 500 so that Stripe retries;
     * paySuccess is idempotent, so a retried event cannot mark the order paid twice.
     */
    @PostMapping("/stripe")
    public Map<String, String> stripeWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            HttpServletResponse response) throws IOException {

        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();

        if (!stripePayUtil.verifyWebhookSignature(payload, stripeSignature)) {
            log.warn("Rejected a Stripe webhook with a missing or invalid signature");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("received", "invalid signature");
            return result;
        }

        try {
            JSONObject event = JSON.parseObject(payload);
            String eventType = event.getString("type");
            if ("checkout.session.completed".equals(eventType)) {
                handleCheckoutSessionCompleted(event);
            } else {
                log.info("Ignoring Stripe event type {}", eventType);
            }
            result.put("received", "ok");
        } catch (Exception e) {
            log.error("Failed to process a Stripe webhook", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            result.put("received", "error");
        }
        return result;
    }

    private void handleCheckoutSessionCompleted(JSONObject event) {
        JSONObject session = event.getJSONObject("data").getJSONObject("object");
        JSONObject metadata = session.getJSONObject("metadata");
        String orderNumber = metadata != null ? metadata.getString("order_number") : null;
        if (orderNumber == null) {
            log.warn("Checkout session {} has no order_number metadata", session.getString("id"));
            return;
        }

        log.info("Stripe checkout completed for order {}", orderNumber);
        orderService.paySuccess(orderNumber);
    }
}
