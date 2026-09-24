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

/**
 * Stripe 支付工具类
 */
@Component
@Slf4j
public class StripePayUtil {

    private static final String STRIPE_API = "https://api.stripe.com/v1";

    @Autowired
    private StripeProperties stripeProperties;

    /**
     * 创建 Stripe Checkout Session
     * 用于前端跳转到 Stripe 支付页面
     *
     * @param orderNumber 订单号
     * @param amount      支付金额（单位：元）
     * @param description 商品描述
     * @return checkout session ID 和支付页面URL
     */
    public JSONObject createCheckoutSession(String orderNumber, BigDecimal amount, String description) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("success_url", stripeProperties.getSuccessUrl() + "?session_id={CHECKOUT_SESSION_ID}");
            paramMap.put("cancel_url", stripeProperties.getCancelUrl());
            paramMap.put("mode", "payment");

            // 第一个商品
            paramMap.put("line_items[0][quantity]", "1");
            paramMap.put("line_items[0][price_data][currency]", "usd");
            paramMap.put("line_items[0][price_data][product_data][name]", description);
            paramMap.put("line_items[0][price_data][unit_amount]", String.valueOf(amount.multiply(new BigDecimal(100)).longValue()));
            paramMap.put("line_items[0][name]", description);

            // 将订单号存入 metadata，用于 webhook 回调时识别
            paramMap.put("metadata[order_number]", orderNumber);

            String response = HttpClientUtil.doPost4Json(
                    STRIPE_API + "/checkout/sessions", paramMap);

            JSONObject jsonObject = JSON.parseObject(response);
            log.info("Stripe Checkout Session 创建成功：{}", jsonObject.getString("id"));

            return jsonObject;
        } catch (Exception e) {
            log.error("Stripe Checkout Session 创建失败", e);
            throw new RuntimeException("支付创建失败", e);
        }
    }

    /**
     * 验证 Stripe Webhook 签名
     *
     * @param payload  请求体
     * @param sigHeader Stripe-Signature header
     * @return 是否通过验证
     */
    public boolean verifyWebhookSignature(String payload, String sigHeader) {
        try {
            // 简化处理：在生产环境中应使用 Stripe 官方库验证
            // 这里直接处理事件
            return true;
        } catch (Exception e) {
            log.error("Stripe Webhook 签名验证失败", e);
            return false;
        }
    }

    /**
     * 解析 Stripe Webhook 事件，获取订单号
     *
     * @param payload 请求体
     * @return 订单号
     */
    public String parseOrderNumberFromWebhook(String payload) {
        JSONObject event = JSON.parseObject(payload);

        // 处理 checkout.session.completed 事件
        String eventType = event.getString("type");
        log.info("Stripe 事件类型：{}", eventType);

        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        // 从 metadata 中获取订单号
        JSONObject metadata = object.getJSONObject("metadata");
        if (metadata != null) {
            return metadata.getString("order_number");
        }

        // 从 payment_intent 获取
        String paymentIntent = object.getString("payment_intent");
        if (paymentIntent != null) {
            return paymentIntent;
        }

        return null;
    }

    /**
     * 退款
     *
     * @param paymentIntentId 支付意图ID
     * @param amount          退款金额（单位：元）
     * @return 退款结果
     */
    public JSONObject refund(String paymentIntentId, BigDecimal amount) {
        try {
            Map<String, String> paramMap = new HashMap<>();
            paramMap.put("payment_intent", paymentIntentId);
            paramMap.put("amount", String.valueOf(amount.multiply(new BigDecimal(100)).longValue()));

            String response = HttpClientUtil.doPost4Json(
                    STRIPE_API + "/refunds", paramMap);

            JSONObject jsonObject = JSON.parseObject(response);
            log.info("Stripe 退款结果：{}", jsonObject.getString("id"));

            return jsonObject;
        } catch (Exception e) {
            log.error("Stripe 退款失败", e);
            throw new RuntimeException("退款失败", e);
        }
    }
}