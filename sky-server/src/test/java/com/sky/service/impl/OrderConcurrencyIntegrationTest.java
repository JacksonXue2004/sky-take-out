package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderService;
import com.sky.utils.StripePayUtil;
import com.sky.support.MySqlIntegrationTest;
import com.sky.support.TestOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Runs the real service and MyBatis mapper against MySQL in a Docker container. */
class OrderConcurrencyIntegrationTest extends MySqlIntegrationTest {

    private static final int ROUNDS = 20;

    // Refunds go to Stripe in production; here we only count them.
    @MockBean
    private StripePayUtil stripePayUtil;

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;

    @BeforeEach
    void stripeAcceptsRefunds() {
        when(stripePayUtil.refund(anyString(), any(), anyString())).thenReturn(new JSONObject());
    }

    @Test
    void confirmAndRejectRacingOnTheSameOrderLetExactlyOneWin() throws Exception {
        int rejectWins = 0;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                Long orderId = insertPaidOrderWaitingForMerchant();
                CountDownLatch start = new CountDownLatch(1);

                Future<Boolean> confirm = pool.submit(attempt(start, () -> {
                    OrdersConfirmDTO dto = new OrdersConfirmDTO();
                    dto.setId(orderId);
                    orderService.confirm(dto);
                }));
                Future<Boolean> reject = pool.submit(attempt(start, () -> {
                    OrdersRejectionDTO dto = new OrdersRejectionDTO();
                    dto.setId(orderId);
                    dto.setRejectionReason("Out of stock");
                    orderService.rejection(dto);
                }));
                start.countDown();

                boolean confirmWon = confirm.get(30, TimeUnit.SECONDS);
                boolean rejectWon = reject.get(30, TimeUnit.SECONDS);

                assertNotEquals(confirmWon, rejectWon, "exactly one of the two actions must succeed");
                Integer finalStatus = orderMapper.getById(orderId).getStatus();
                assertEquals(confirmWon ? Orders.CONFIRMED : Orders.CANCELLED, finalStatus);
                if (rejectWon) {
                    rejectWins++;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // A refund is issued only for the rounds the rejection won, never for the losing request.
        verify(stripePayUtil, times(rejectWins)).refund(anyString(), any(), anyString());
    }

    private Long insertPaidOrderWaitingForMerchant() {
        Orders orders = TestOrders.paidOrderWaitingForMerchant(1L);
        orderMapper.insert(orders);
        return orders.getId();
    }

    /** Waits for the start signal, runs the action, and reports whether it succeeded. */
    private static Callable<Boolean> attempt(CountDownLatch start, ThrowingRunnable action) {
        return () -> {
            start.await();
            try {
                action.run();
                return true;
            } catch (OrderBusinessException e) {
                return false;
            }
        };
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
