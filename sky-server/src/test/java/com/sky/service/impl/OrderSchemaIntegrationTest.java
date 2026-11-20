package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.support.MySqlIntegrationTest;
import com.sky.support.TestOrders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** Guarantees the database enforces on its own, whatever the application code does. */
class OrderSchemaIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    private OrderMapper orderMapper;

    @Test
    void theDatabaseRejectsASecondOrderWithTheSameNumber() {
        Orders first = TestOrders.order(1L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        Orders second = TestOrders.order(2L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        second.setNumber(first.getNumber());

        orderMapper.insert(first);
        assertThrows(DuplicateKeyException.class, () -> orderMapper.insert(second));
    }

    @Test
    void aCustomerCannotStoreTheSameIdempotencyKeyTwice() {
        Orders first = TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        first.setIdempotencyKey("key-shared-by-two-orders");
        Orders second = TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        second.setIdempotencyKey("key-shared-by-two-orders");
        Orders otherCustomer = TestOrders.order(4L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        otherCustomer.setIdempotencyKey("key-shared-by-two-orders");

        orderMapper.insert(first);
        assertThrows(DuplicateKeyException.class, () -> orderMapper.insert(second));
        // Keys are scoped to one customer, and orders without a key never collide.
        orderMapper.insert(otherCustomer);
        orderMapper.insert(TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
        orderMapper.insert(TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
    }
}
