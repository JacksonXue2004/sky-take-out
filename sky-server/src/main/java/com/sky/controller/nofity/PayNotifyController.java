package com.sky.controller.nofity;

import com.sky.service.StripeWebhookService;
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
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private StripeWebhookService stripeWebhookService;
    @Autowired
    private StripePayUtil stripePayUtil;

    /**
     * Stripe webhook. The body is read as raw text because the signature covers the exact bytes
     * Stripe sent.
     * <ul>
     *     <li>400: missing, wrong or expired signature. Nothing is stored, because anyone can send these.</li>
     *     <li>500: processing failed, so Stripe delivers the event again later.</li>
     *     <li>200: everything else, including events that were rejected after the signature check;
     *     repeating those would not help, and they are kept in webhook_event for review.</li>
     * </ul>
     */
    @PostMapping("/stripe")
    public Map<String, String> stripeWebhook(
            HttpServletRequest request,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature,
            HttpServletResponse response) throws IOException {

        String payload = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();

        if (!stripePayUtil.verifyWebhookSignature(payload, stripeSignature)) {
            log.warn("Rejected a Stripe webhook with a missing, invalid or expired signature");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            result.put("received", "invalid signature");
            return result;
        }

        StripeWebhookService.Outcome outcome;
        try {
            outcome = stripeWebhookService.handle(payload);
        } catch (RuntimeException e) {
            // For example, the database is down and the event could not even be recorded.
            log.error("Failed to record a Stripe webhook", e);
            outcome = StripeWebhookService.Outcome.FAILED;
        }
        if (outcome == StripeWebhookService.Outcome.FAILED) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
        result.put("received", outcome.name().toLowerCase(Locale.ROOT));
        return result;
    }
}
