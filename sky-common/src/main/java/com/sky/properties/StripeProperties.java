package com.sky.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.stripe")
@Data
public class StripeProperties {

    // Stripe 密钥（sk_test_xxx）
    private String secretKey;

    // Stripe 发布密钥（pk_test_xxx）
    private String publishableKey;

    // Stripe Webhook 密钥（whsec_xxx）
    private String webhookSecret;

    // 支付成功后的前端跳转地址
    private String successUrl;

    // 支付取消后的前端跳转地址
    private String cancelUrl;

}