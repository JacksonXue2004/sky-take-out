package com.sky.task;

import com.alibaba.fastjson.JSONObject;
import com.sky.entity.RefundTask;
import com.sky.exception.StripeApiException;
import com.sky.mapper.RefundTaskMapper;
import com.sky.metrics.OrderMetrics;
import com.sky.utils.StripePayUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundWorkerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("25.00");

    @Mock
    private RefundTaskMapper refundTaskMapper;
    @Mock
    private StripePayUtil stripePayUtil;
    @Mock
    private OrderMetrics orderMetrics;

    @InjectMocks
    private RefundWorker worker;

    @Test
    void aDueRefundIsSentWithItsIdempotencyKeyAndMarkedSucceeded() {
        due(task(0));
        JSONObject refund = new JSONObject();
        refund.put("id", "re_test_1");
        when(stripePayUtil.refund("pi_test_1", AMOUNT, "refund-order-1")).thenReturn(refund);

        worker.processDueRefunds();

        verify(refundTaskMapper).markSucceeded(eq(1L), eq("re_test_1"), any());
        verify(orderMetrics).refund("succeeded");
    }

    @Test
    void aTemporaryFailureIsRetriedLater() {
        due(task(0));
        when(stripePayUtil.refund(anyString(), any(), anyString()))
                .thenThrow(new StripeApiException(503, "Stripe returned 503", true));

        LocalDateTime before = LocalDateTime.now();
        worker.processDueRefunds();

        ArgumentCaptor<LocalDateTime> next = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundTaskMapper).reschedule(eq(1L), next.capture(), anyString(), any());
        // First retry about one minute later.
        long secondsLater = Duration.between(before, next.getValue()).getSeconds();
        assertTrue(secondsLater >= 59 && secondsLater <= 61, "retry in " + secondsLater + "s");
        verify(refundTaskMapper, never()).markFailed(any(), any(), any());
    }

    @Test
    void theLastAttemptMovesTheRefundToFailed() {
        due(task(RefundWorker.MAX_ATTEMPTS - 1));
        when(stripePayUtil.refund(anyString(), any(), anyString()))
                .thenThrow(new StripeApiException("Could not reach Stripe: timeout", new RuntimeException()));

        worker.processDueRefunds();

        verify(refundTaskMapper).markFailed(eq(1L), anyString(), any());
        verify(refundTaskMapper, never()).reschedule(any(), any(), any(), any());
        verify(orderMetrics).refund("failed");
    }

    @Test
    void aRequestStripeRefusesFailsAtOnceBecauseRetryingCannotHelp() {
        due(task(0));
        when(stripePayUtil.refund(anyString(), any(), anyString()))
                .thenThrow(new StripeApiException(400, "Stripe returned 400: charge already refunded", false));

        worker.processDueRefunds();

        verify(refundTaskMapper).markFailed(eq(1L), eq("Stripe returned 400: charge already refunded"), any());
    }

    @Test
    void aTaskAnotherInstanceClaimedFirstIsLeftAlone() {
        when(refundTaskMapper.findDue(any(), anyInt())).thenReturn(Collections.singletonList(task(0)));
        when(refundTaskMapper.claim(eq(1L), any())).thenReturn(0);

        worker.processDueRefunds();

        verifyNoInteractions(stripePayUtil);
    }

    @Test
    void tasksAbandonedMidCallAreHandedBackFirst() {
        when(refundTaskMapper.findDue(any(), anyInt())).thenReturn(Collections.emptyList());

        LocalDateTime before = LocalDateTime.now();
        worker.processDueRefunds();

        ArgumentCaptor<LocalDateTime> claimedBefore = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(refundTaskMapper).releaseStuck(claimedBefore.capture(), any());
        long minutes = Duration.between(claimedBefore.getValue(), before).toMinutes();
        assertTrue(minutes >= 4 && minutes <= 5, minutes + " minutes");
    }

    private void due(RefundTask task) {
        when(refundTaskMapper.findDue(any(), anyInt())).thenReturn(Collections.singletonList(task));
        when(refundTaskMapper.claim(eq(task.getId()), any())).thenReturn(1);
    }

    private static RefundTask task(int attempts) {
        return RefundTask.builder()
                .id(1L)
                .orderId(10L)
                .orderNumber("order-1")
                .paymentIntentId("pi_test_1")
                .amount(AMOUNT)
                .status(RefundTask.PENDING)
                .attempts(attempts)
                .build();
    }
}
