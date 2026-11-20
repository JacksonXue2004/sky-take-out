package com.sky.task;

import com.alibaba.fastjson.JSONObject;
import com.sky.exception.StripeApiException;
import com.sky.service.RefundService;
import com.sky.support.MySqlIntegrationTest;
import com.sky.utils.StripePayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The refund outbox against the real database: queuing, retry scheduling and crash recovery. */
class RefundWorkerIntegrationTest extends MySqlIntegrationTest {

    @MockBean
    private StripePayUtil stripePayUtil;

    @Autowired
    private RefundService refundService;
    @Autowired
    private RefundWorker refundWorker;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void emptyOutbox() {
        jdbcTemplate.update("delete from refund_task");
    }

    @Test
    void anOrderIsQueuedForRefundOnlyOnce() {
        refundService.enqueue(5001L, "order-5001", "pi_test_5001", new BigDecimal("12.50"));
        refundService.enqueue(5001L, "order-5001", "pi_test_5001", new BigDecimal("12.50"));

        assertEquals(1, jdbcTemplate.queryForObject("select count(*) from refund_task where order_id = 5001", Integer.class));
    }

    @Test
    void aFailedCallWaitsForItsRetryTimeInsteadOfHammeringStripe() {
        refundService.enqueue(5002L, "order-5002", "pi_test_5002", new BigDecimal("12.50"));
        when(stripePayUtil.refund(anyString(), any(), anyString()))
                .thenThrow(new StripeApiException(500, "Stripe returned 500", true));

        refundWorker.processDueRefunds();
        refundWorker.processDueRefunds(); // not due again yet

        verify(stripePayUtil, times(1)).refund(anyString(), any(), anyString());
        Map<String, Object> task = task(5002L);
        assertEquals("PENDING", task.get("status"));
        assertEquals(1, ((Number) task.get("attempts")).intValue());
        LocalDateTime nextAttempt = jdbcTemplate.queryForObject(
                "select next_attempt_at from refund_task where order_id = 5002", LocalDateTime.class);
        assertTrue(nextAttempt.isAfter(LocalDateTime.now()), "next attempt at " + nextAttempt);
        assertEquals("Stripe returned 500", task.get("last_error"));
    }

    @Test
    void aTaskLeftProcessingByACrashedWorkerIsSentAgainWithTheSameIdempotencyKey() {
        refundService.enqueue(5003L, "order-5003", "pi_test_5003", new BigDecimal("12.50"));
        // A worker claimed it ten minutes ago and never finished.
        jdbcTemplate.update("update refund_task set status = 'PROCESSING', updated_at = ? where order_id = 5003",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        JSONObject refund = new JSONObject();
        refund.put("id", "re_test_5003");
        when(stripePayUtil.refund("pi_test_5003", new BigDecimal("12.50"), "refund-order-5003")).thenReturn(refund);

        refundWorker.processDueRefunds();

        // If the crashed worker's call had reached Stripe, this key makes Stripe return that refund again.
        verify(stripePayUtil).refund(eq("pi_test_5003"), any(), eq("refund-order-5003"));
        Map<String, Object> task = task(5003L);
        assertEquals("SUCCEEDED", task.get("status"));
        assertEquals("re_test_5003", task.get("stripe_refund_id"));
    }

    private Map<String, Object> task(long orderId) {
        return jdbcTemplate.queryForMap("select * from refund_task where order_id = ?", orderId);
    }
}
