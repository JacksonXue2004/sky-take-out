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

/**
 * Stripe 支付回调通知控制器
 */
@RestController
@RequestMapping("/notify")
@Slf4j
public class PayNotifyController {

    @Autowired
    private OrderService orderService;

    /**
     * Stripe Webhook 端点
     * 接收 Stripe 支付事件通知
     *
     * @param payload 请求体
     * @param stripeSignature Stripe 签名
     * @param response 响应
     */
    @PostMapping("/stripe")
    public Map<String, String> stripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String stripeSignature,
            HttpServletResponse response) {

        log.info("收到 Stripe Webhook 通知：{}", payload);

        try {
            JSONObject event = JSON.parseObject(payload);
            String eventType = event.getString("type");

            // 处理不同事件类型
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
                    log.info("未处理的事件类型：{}", eventType);
            }

            // 返回 200 给 Stripe
            Map<String, String> result = new HashMap<>();
            result.put("received", "ok");
            return result;

        } catch (Exception e) {
            log.error("处理 Stripe Webhook 失败", e);
            Map<String, String> result = new HashMap<>();
            result.put("received", "error");
            return result;
        }
    }

    /**
     * 处理 checkout session 完成事件
     */
    private void handleCheckoutSessionCompleted(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        // 从 metadata 获取订单号
        JSONObject metadata = object.getJSONObject("metadata");
        String orderNumber = metadata != null ? metadata.getString("order_number") : null;

        // 如果 metadata 没有，尝试从 payment_intent 获取
        if (orderNumber == null) {
            String paymentIntent = object.getString("payment_intent");
            orderNumber = paymentIntent;
        }

        if (orderNumber != null) {
            log.info("Checkout Session 完成，订单号：{}", orderNumber);
            // 调用业务方法更新订单状态
            orderService.paySuccess(orderNumber);
        }
    }

    /**
     * 处理支付成功事件
     */
    private void handlePaymentIntentSucceeded(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        String paymentIntentId = object.getString("id");
        log.info("支付成功，PaymentIntent ID：{}", paymentIntentId);

        // 使用 paymentIntentId 作为订单号（需要在业务中关联）
        orderService.paySuccess(paymentIntentId);
    }

    /**
     * 处理支付失败事件
     */
    private void handlePaymentFailed(JSONObject event) {
        JSONObject data = event.getJSONObject("data");
        JSONObject object = data.getJSONObject("object");

        String paymentIntentId = object.getString("id");
        log.info("支付失败，PaymentIntent ID：{}", paymentIntentId);
    }
}