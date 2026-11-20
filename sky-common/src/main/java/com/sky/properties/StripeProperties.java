package com.sky.properties;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.stripe")
@Data
public class StripeProperties {

    /** Only tests point this somewhere else. */
    private String apiBase = "https://api.stripe.com/v1";

    @ToString.Exclude
    private String secretKey;


    private String publishableKey;


    @ToString.Exclude
    private String webhookSecret;


    private String successUrl;


    private String cancelUrl;

}
