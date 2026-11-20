package com.sky.service.impl;

import com.sky.entity.RefundTask;
import com.sky.mapper.RefundTaskMapper;
import com.sky.service.RefundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class RefundServiceImpl implements RefundService {

    @Autowired
    private RefundTaskMapper refundTaskMapper;

    @Override
    public void enqueue(Long orderId, String orderNumber, String paymentIntentId, BigDecimal amount) {
        // MySQL rounds fractional seconds when it stores a datetime, so 12:00:00.7 would be saved as
        // 12:00:01 and the task would not count as due until a second later. Whole seconds avoid that.
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        RefundTask task = RefundTask.builder()
                .orderId(orderId)
                .orderNumber(orderNumber)
                .paymentIntentId(paymentIntentId)
                .amount(amount)
                .status(RefundTask.PENDING)
                .attempts(0)
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            refundTaskMapper.insert(task);
            log.info("Queued a refund of {} for order {}", amount, orderNumber);
        } catch (DuplicateKeyException alreadyQueued) {
            log.warn("Order {} already has a refund task; not queuing another", orderNumber);
        }
    }
}
