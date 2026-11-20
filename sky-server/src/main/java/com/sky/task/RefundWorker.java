package com.sky.task;

import com.alibaba.fastjson.JSONObject;
import com.sky.entity.RefundTask;
import com.sky.exception.StripeApiException;
import com.sky.mapper.RefundTaskMapper;
import com.sky.utils.StripePayUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Sends queued refunds to Stripe, outside any order transaction.
 * <p>
 * A failed call is retried with growing delays. After the last attempt, or when Stripe refuses
 * the request outright, the task stays FAILED for a person to handle; that is the dead-letter
 * state. Each task is claimed with a conditional update, so several backend instances can run this
 * job at once. Every call carries the Idempotency-Key refund-&lt;order number&gt;, so even a retry after
 * a timeout, when Stripe may already have refunded, cannot refund twice.
 */
@Component
@Slf4j
public class RefundWorker {

    static final int MAX_ATTEMPTS = 6;
    /** Wait after failed attempt n before attempt n + 1: 1 min, 5 min, 15 min, 1 h, 3 h. */
    private static final long[] BACKOFF_MINUTES = {1, 5, 15, 60, 180};
    /** A task still PROCESSING after this long belongs to a worker that died mid-call. */
    private static final long STUCK_AFTER_MINUTES = 5;
    private static final int BATCH_SIZE = 20;
    private static final int MAX_ERROR_LENGTH = 500;

    @Autowired
    private RefundTaskMapper refundTaskMapper;
    @Autowired
    private StripePayUtil stripePayUtil;

    @Scheduled(fixedDelayString = "${sky.refund.poll-interval-ms:10000}",
            initialDelayString = "${sky.refund.initial-delay-ms:10000}")
    public void processDueRefunds() {
        LocalDateTime now = LocalDateTime.now();
        int released = refundTaskMapper.releaseStuck(now.minusMinutes(STUCK_AFTER_MINUTES), now);
        if (released > 0) {
            log.warn("Released {} refund task(s) whose worker stopped mid-call", released);
        }
        for (RefundTask task : refundTaskMapper.findDue(now, BATCH_SIZE)) {
            if (refundTaskMapper.claim(task.getId(), LocalDateTime.now()) == 0) {
                continue; // another instance claimed it first
            }
            send(task);
        }
    }

    private void send(RefundTask task) {
        try {
            JSONObject refund = stripePayUtil.refund(
                    task.getPaymentIntentId(), task.getAmount(), "refund-" + task.getOrderNumber());
            refundTaskMapper.markSucceeded(task.getId(), refund.getString("id"), LocalDateTime.now());
            log.info("Refunded order {} ({})", task.getOrderNumber(), refund.getString("id"));
        } catch (StripeApiException e) {
            recordFailure(task, e.getMessage(), e.isRetryable());
        } catch (RuntimeException e) {
            recordFailure(task, e.toString(), true);
        }
    }

    private void recordFailure(RefundTask task, String error, boolean retryable) {
        int attempts = task.getAttempts() + 1;
        LocalDateTime now = LocalDateTime.now();
        String message = error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
        if (!retryable || attempts >= MAX_ATTEMPTS) {
            refundTaskMapper.markFailed(task.getId(), message, now);
            log.error("Refund for order {} FAILED after {} attempt(s) and needs manual handling: {}",
                    task.getOrderNumber(), attempts, message);
            return;
        }
        LocalDateTime next = now.plusMinutes(BACKOFF_MINUTES[attempts - 1]);
        refundTaskMapper.reschedule(task.getId(), next, message, now);
        log.warn("Refund for order {} failed (attempt {} of {}); retrying at {}: {}",
                task.getOrderNumber(), attempts, MAX_ATTEMPTS, next, message);
    }
}
