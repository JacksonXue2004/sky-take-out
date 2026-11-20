package com.sky.service;

import java.math.BigDecimal;

public interface RefundService {

    /**
     * Queues a refund in the outbox. Call it inside the transaction that cancels the order, so
     * that the cancellation and the refund task are saved together or not at all.
     * Queuing the same order twice keeps the first task.
     */
    void enqueue(Long orderId, String orderNumber, String paymentIntentId, BigDecimal amount);
}
