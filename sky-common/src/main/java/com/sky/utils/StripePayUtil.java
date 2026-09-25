package com.sky.utils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.StripeProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class StripePayUtil {

    private static final String STRIPE_API = "https://api.stripe.com/v1";

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

    public boolean verifyWebhookSignature(String payload, String sigHeader) {
        try {


            return true;
        } catch (Exception e) {
            log.error("Application event: {}", e);
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