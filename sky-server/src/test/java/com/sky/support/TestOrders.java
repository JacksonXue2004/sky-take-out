package com.sky.support;

import com.sky.entity.Orders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Orders for inserting straight into the test database. */
public final class TestOrders {

    private TestOrders() {
    }

    public static Orders order(Long userId, Integer status, Integer payStatus) {
        return Orders.builder()
                .number(UUID.randomUUID().toString().replace("-", ""))
                .status(status)
                .payStatus(payStatus)
                .payMethod(1)
                .userId(userId)
                .addressBookId(1L)
                .orderTime(LocalDateTime.now())
                .checkoutTime(Orders.PAID.equals(payStatus) ? LocalDateTime.now() : null)
                .amount(new BigDecimal("25.00"))
                .deliveryStatus(1)
                .tablewareStatus(1)
                .build();
    }

    /** A paid order the merchant has not accepted yet. */
    public static Orders paidOrderWaitingForMerchant(Long userId) {
        return order(userId, Orders.TO_BE_CONFIRMED, Orders.PAID);
    }
}
