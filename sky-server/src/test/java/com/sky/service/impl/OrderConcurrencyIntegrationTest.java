package com.sky.service.impl;

import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.entity.Orders;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderService;
import com.sky.utils.StripePayUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

/**
 * Runs the real service and MyBatis mapper against MySQL in a Docker container.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
class OrderConcurrencyIntegrationTest {

    private static final int ROUNDS = 20;

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("sky_take_out")
            .withInitScript("db/orders-schema.sql");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
    }

    // Refunds go to Stripe in production; here we only count them.
    @MockBean
    private StripePayUtil stripePayUtil;

    @Autowired
    private OrderService orderService;
    @Autowired
    private OrderMapper orderMapper;

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
        verify(stripePayUtil, times(rejectWins)).refund(anyString(), any());
    }

    private Long insertPaidOrderWaitingForMerchant() {
        Orders orders = Orders.builder()
                .number(String.valueOf(System.nanoTime()))
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .payMethod(1)
                .userId(1L)
                .addressBookId(1L)
                .orderTime(LocalDateTime.now())
                .checkoutTime(LocalDateTime.now())
                .amount(new BigDecimal("25.00"))
                .deliveryStatus(1)
                .tablewareStatus(1)
                .build();
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
