package com.sky.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A refund waiting to be sent to Stripe (the refund outbox). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundTask {

    /** Waiting for its next attempt, at nextAttemptAt. */
    public static final String PENDING = "PENDING";
    /** Claimed by a worker that is calling Stripe right now. */
    public static final String PROCESSING = "PROCESSING";
    public static final String SUCCEEDED = "SUCCEEDED";
    /** Out of attempts, or Stripe refused the request; a person has to look at it. */
    public static final String FAILED = "FAILED";

    private Long id;

    private Long orderId;

    private String orderNumber;

    private String paymentIntentId;

    private BigDecimal amount;

    private String status;

    private Integer attempts;

    private LocalDateTime nextAttemptAt;

    private String lastError;

    private String stripeRefundId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
