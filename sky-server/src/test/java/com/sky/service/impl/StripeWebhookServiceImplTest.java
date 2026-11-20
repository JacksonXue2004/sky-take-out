package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.entity.WebhookEvent;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.WebhookEventMapper;
import com.sky.service.OrderService;
import com.sky.service.RefundService;
import com.sky.service.StripeWebhookService.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static com.sky.support.StripeEventSimulator.checkoutCompleted;
import static com.sky.support.StripeEventSimulator.checkoutEvent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceImplTest {

    private static final String ORDER_NUMBER = "0f8e7d6c5b4a39281706f5e4d3c2b1a0";
    private static final String PAYMENT_INTENT = "pi_test_1";

    @Mock
    private WebhookEventMapper webhookEventMapper;
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderService orderService;
    @Mock
    private RefundService refundService;

    @InjectMocks
    private StripeWebhookServiceImpl service;

    @Test
    void aPaidCheckoutMarksTheOrderPaid() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT, null));
        when(orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT)).thenReturn(true);

        assertEquals(Outcome.PROCESSED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));

        WebhookEvent finished = finishedEvent();
        assertEquals("evt_1", finished.getEventId());
        assertEquals(WebhookEvent.PROCESSED, finished.getStatus());
        assertEquals(ORDER_NUMBER, finished.getOrderNumber());
    }

    @Test
    void aRepeatOfAFinishedEventDoesNothing() {
        doThrow(new DuplicateKeyException("uk_webhook_event_event_id")).when(webhookEventMapper).insert(any());
        when(webhookEventMapper.getByEventId("evt_1"))
                .thenReturn(WebhookEvent.builder().id(5L).eventId("evt_1").status(WebhookEvent.PROCESSED).build());

        assertEquals(Outcome.DUPLICATE, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));

        verifyNoInteractions(orderService, orderMapper);
        verify(webhookEventMapper, never()).finish(any());
    }

    @Test
    void aRepeatOfAFailedEventIsProcessedAgain() {
        doThrow(new DuplicateKeyException("uk_webhook_event_event_id")).when(webhookEventMapper).insert(any());
        when(webhookEventMapper.getByEventId("evt_1"))
                .thenReturn(WebhookEvent.builder().id(5L).eventId("evt_1").status(WebhookEvent.FAILED).build());
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT, null));
        when(orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT)).thenReturn(true);

        assertEquals(Outcome.PROCESSED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));

        verify(webhookEventMapper).countAttempt(5L);
        assertEquals(WebhookEvent.PROCESSED, finishedEvent().getStatus());
    }

    @Test
    void anAmountThatDoesNotMatchTheOrderIsRejected() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT, null));

        assertEquals(Outcome.REJECTED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 1, PAYMENT_INTENT)));

        verify(orderService, never()).paySuccess(any(), any());
        WebhookEvent finished = finishedEvent();
        assertEquals(WebhookEvent.REJECTED, finished.getStatus());
        assertTrue(finished.getError().contains("amount_total is 1"), finished.getError());
    }

    @Test
    void anotherCurrencyIsRejected() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT, null));

        assertEquals(Outcome.REJECTED, service.handle(checkoutEvent("evt_1", "checkout.session.completed",
                ORDER_NUMBER, 2500, "eur", "paid", PAYMENT_INTENT)));
        verify(orderService, never()).paySuccess(any(), any());
    }

    @Test
    void anUnknownOrderIsRejected() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(null);

        assertEquals(Outcome.REJECTED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));
        verify(orderService, never()).paySuccess(any(), any());
    }

    @Test
    void aCheckoutWhosePaymentHasNotClearedIsIgnored() {
        assertEquals(Outcome.IGNORED, service.handle(checkoutEvent("evt_1", "checkout.session.completed",
                ORDER_NUMBER, 2500, "usd", "unpaid", PAYMENT_INTENT)));
        verifyNoInteractions(orderMapper, orderService);
    }

    @Test
    void otherEventTypesAreIgnored() {
        assertEquals(Outcome.IGNORED, service.handle(checkoutEvent("evt_1", "customer.created",
                ORDER_NUMBER, 2500, "usd", "paid", PAYMENT_INTENT)));
        verifyNoInteractions(orderMapper, orderService);
    }

    @Test
    void aPayloadThatIsNotAnEventIsStoredAsRejected() {
        assertEquals(Outcome.REJECTED, service.handle("this is not json"));

        ArgumentCaptor<WebhookEvent> stored = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventMapper).insert(stored.capture());
        assertNull(stored.getValue().getEventId());
        assertEquals(WebhookEvent.REJECTED, stored.getValue().getStatus());
        assertEquals("this is not json", stored.getValue().getPayload());
    }

    @Test
    void anEventWithoutTheSessionIsRejected() {
        assertEquals(Outcome.REJECTED,
                service.handle("{\"id\":\"evt_2\",\"type\":\"checkout.session.completed\",\"data\":{}}"));

        assertTrue(finishedEvent().getError().contains("data.object is missing"));
        verifyNoInteractions(orderService);
    }

    @Test
    void aPaymentForAnOrderThatWasAlreadyCancelledIsFlaggedForRefund() {
        // The payment timeout cancelled the order before the customer finished paying.
        when(orderMapper.getByNumber(ORDER_NUMBER))
                .thenReturn(order(Orders.PENDING_PAYMENT, null), order(Orders.CANCELLED, null));
        when(orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT)).thenReturn(false);

        assertEquals(Outcome.REJECTED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));

        assertTrue(finishedEvent().getError().contains("must be refunded"), finishedEvent().getError());
        verify(refundService).enqueue(100L, ORDER_NUMBER, PAYMENT_INTENT, new BigDecimal("25.00"));
    }

    @Test
    void aSecondEventForTheSamePaymentIsIgnored() {
        when(orderMapper.getByNumber(ORDER_NUMBER))
                .thenReturn(order(Orders.PENDING_PAYMENT, null), order(Orders.TO_BE_CONFIRMED, PAYMENT_INTENT));
        when(orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT)).thenReturn(false);

        assertEquals(Outcome.IGNORED, service.handle(checkoutEvent("evt_9", "checkout.session.async_payment_succeeded",
                ORDER_NUMBER, 2500, "usd", "paid", PAYMENT_INTENT)));
    }

    @Test
    void aDatabaseFailureMarksTheEventFailedSoStripeRetries() {
        when(orderMapper.getByNumber(ORDER_NUMBER)).thenReturn(order(Orders.PENDING_PAYMENT, null));
        when(orderService.paySuccess(ORDER_NUMBER, PAYMENT_INTENT)).thenThrow(new IllegalStateException("database is down"));

        assertEquals(Outcome.FAILED, service.handle(checkoutCompleted("evt_1", ORDER_NUMBER, 2500, PAYMENT_INTENT)));

        assertEquals(WebhookEvent.FAILED, finishedEvent().getStatus());
    }

    private WebhookEvent finishedEvent() {
        ArgumentCaptor<WebhookEvent> finished = ArgumentCaptor.forClass(WebhookEvent.class);
        verify(webhookEventMapper).finish(finished.capture());
        return finished.getValue();
    }

    private static Orders order(Integer status, String paymentIntentId) {
        return Orders.builder()
                .id(100L)
                .number(ORDER_NUMBER)
                .status(status)
                .amount(new BigDecimal("25.00"))
                .paymentIntentId(paymentIntentId)
                .build();
    }
}
