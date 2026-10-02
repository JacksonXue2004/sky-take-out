package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTaskTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderTask orderTask;

    @Test
    void timeoutJobCancelsOnlyOrdersThatAreStillUnpaid() {
        Orders first = Orders.builder().id(1L).number("A1").status(Orders.PENDING_PAYMENT).build();
        Orders paidMeanwhile = Orders.builder().id(2L).number("A2").status(Orders.PENDING_PAYMENT).build();
        when(orderMapper.getByStatusAndOrderTimeLT(eq(Orders.PENDING_PAYMENT), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(first, paidMeanwhile));
        // The second order was paid after the query, so its conditional update matches no row.
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.PENDING_PAYMENT))).thenReturn(1, 0);

        orderTask.processTimeoutOrder();

        ArgumentCaptor<Orders> updates = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper, times(2)).updateStatus(updates.capture(), eq(Orders.PENDING_PAYMENT));
        assertTrue(updates.getAllValues().stream().allMatch(o -> Orders.CANCELLED.equals(o.getStatus())));
        verify(orderMapper, never()).update(any());
    }

    @Test
    void deliveryJobCompletesOnlyOrdersStillInDelivery() {
        Orders delivering = Orders.builder().id(3L).number("B1").status(Orders.DELIVERY_IN_PROGRESS).build();
        when(orderMapper.getByStatusAndOrderTimeLT(eq(Orders.DELIVERY_IN_PROGRESS), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(delivering));
        when(orderMapper.updateStatus(any(Orders.class), eq(Orders.DELIVERY_IN_PROGRESS))).thenReturn(1);

        orderTask.processDeliveryOrder();

        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).updateStatus(update.capture(), eq(Orders.DELIVERY_IN_PROGRESS));
        assertTrue(Orders.COMPLETED.equals(update.getValue().getStatus()));
        verify(orderMapper, never()).update(any());
    }
}
