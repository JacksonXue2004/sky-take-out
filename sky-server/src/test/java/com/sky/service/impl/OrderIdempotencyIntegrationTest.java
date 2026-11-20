package com.sky.service.impl;

import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.service.OrderService;
import com.sky.support.MySqlIntegrationTest;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Replays one checkout many times at once, the way a double click or a client retry would. */
class OrderIdempotencyIntegrationTest extends MySqlIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentRequestsWithOneKeyPlaceExactlyOneOrder() throws Exception {
        long userId = 900_001L;
        long addressId = insertAddress(userId);
        insertCartLine(userId, "Burger", 3L, 2, "9.50");
        insertCartLine(userId, "Fries", 4L, 1, "3.25");
        String key = UUID.randomUUID().toString();

        Set<Long> orderIds = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<OrderSubmitVO>> results = new ArrayList<>();
            for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    BaseContext.setCurrentId(userId);
                    try {
                        OrdersSubmitDTO dto = new OrdersSubmitDTO();
                        dto.setAddressBookId(addressId);
                        dto.setPayMethod(1);
                        dto.setDeliveryStatus(1);
                        dto.setTablewareStatus(1);
                        return orderService.submitOrder(dto, key);
                    } finally {
                        BaseContext.removeCurrentId();
                    }
                }));
            }
            start.countDown();
            for (Future<OrderSubmitVO> result : results) {
                // Every request succeeds and gets the same order back.
                orderIds.add(result.get(30, TimeUnit.SECONDS).getId());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, orderIds.size(), "all requests must return the same order");
        Long orderId = orderIds.iterator().next();
        assertEquals(1, count("select count(*) from orders where user_id = ?", userId));
        assertEquals(2, count("select count(*) from order_detail where order_id = ?", orderId));
        assertEquals(0, count("select count(*) from shopping_cart where user_id = ?", userId));
        assertEquals("22.25", jdbcTemplate.queryForObject(
                "select amount from orders where id = ?", String.class, orderId));
    }

    private long insertAddress(long userId) {
        jdbcTemplate.update("insert into address_book (user_id, consignee, phone, detail, is_default) values (?, ?, ?, ?, 1)",
                userId, "Alex", "4085550100", "1 Main St");
        return jdbcTemplate.queryForObject("select max(id) from address_book where user_id = ?", Long.class, userId);
    }

    private void insertCartLine(long userId, String name, long dishId, int number, String amount) {
        jdbcTemplate.update("insert into shopping_cart (name, user_id, dish_id, number, amount, create_time) values (?, ?, ?, ?, ?, now())",
                name, userId, dishId, number, amount);
    }

    private int count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arg);
    }
}
