package com.sky.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaymentVO implements Serializable {
    // 微信支付相关字段（保留兼容）
    private String nonceStr;
    private String paySign;
    private String timeStamp;
    private String signType;
    private String packageStr;

    // Stripe 支付相关字段
    private String sessionId;       // Stripe Checkout Session ID
    private String checkoutUrl;     // Stripe 支付页面 URL
}
