package com.sky.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * All allowed order status changes, defined in one place.
 * Each action ends in one target status and may only start from the listed statuses.
 */
public enum OrderTransition {

    PAY(Orders.TO_BE_CONFIRMED, Orders.PENDING_PAYMENT),
    CONFIRM(Orders.CONFIRMED, Orders.TO_BE_CONFIRMED),
    REJECT(Orders.CANCELLED, Orders.TO_BE_CONFIRMED),
    DELIVER(Orders.DELIVERY_IN_PROGRESS, Orders.CONFIRMED),
    COMPLETE(Orders.COMPLETED, Orders.DELIVERY_IN_PROGRESS),
    USER_CANCEL(Orders.CANCELLED, Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED),
    MERCHANT_CANCEL(Orders.CANCELLED,
            Orders.PENDING_PAYMENT, Orders.TO_BE_CONFIRMED, Orders.CONFIRMED, Orders.DELIVERY_IN_PROGRESS),
    PAYMENT_TIMEOUT(Orders.CANCELLED, Orders.PENDING_PAYMENT);

    private final Integer targetStatus;
    private final Set<Integer> sourceStatuses;

    OrderTransition(Integer targetStatus, Integer... sourceStatuses) {
        this.targetStatus = targetStatus;
        this.sourceStatuses = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(sourceStatuses)));
    }

    public Integer getTargetStatus() {
        return targetStatus;
    }

    public boolean canStartFrom(Integer status) {
        return sourceStatuses.contains(status);
    }
}
