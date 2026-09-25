package com.sky.controller.nofity;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/stripe")
    public Map<String, String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignature,
            HttpServletResponse response) {

        log.info("Application event: {}", payload);

        try {
            JSONObject event = JSON.parseObject(payload);
            String eventType = event.getString("type");


            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                case "payment_intent.payment_failed":
                    handlePaymentFailed(event);
                    break;
                default:
                    log.info("Application event: {}", eventType);
            }


            Map<String, String> result = new HashMap<>();
            result.put("received", "ok");
            return result;

        } catch (Exception e) {
            log.error("Application event: {}", e);
            Map<String, String> result = new HashMap<>();
            result.put("received", "error");
            return result;
        }
    }

    private void handleCheckoutSessionCompleted(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");


        JSONObject metadata = object.getJSONObject("metadata");
        String orderNumber = metadata != null ? metadata.getString("order_number") : null;


        if (orderNumber == null) {
            String paymentIntent = object.getString("payment_intent");
            orderNumber = paymentIntent;
        }

        if (orderNumber != null) {
            log.info("Application event: {}", orderNumber);

            orderService.paySuccess(orderNumber);
        }
    }

    private void handlePaymentIntentSucceeded(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        String paymentIntentId = object.getString("id");
        log.info("Application event: {}", paymentIntentId);


        orderService.paySuccess(paymentIntentId);
    }

    private void handlePaymentFailed(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        String paymentIntentId = object.getString("id");
        log.info("Application event: {}", paymentIntentId);
    }
}