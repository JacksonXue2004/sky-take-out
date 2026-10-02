package com.sky.task;

import com.sky.entity.OrderTransition;
import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    @Scheduled(cron = "0 * * * * ? ")
    public void processTimeoutOrder(){
        log.info("Cancelling orders that are still unpaid after 15 minutes");

        LocalDateTime time = LocalDateTime.now().plusMinutes(-15);


        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.PENDING_PAYMENT, time);

        if(ordersList != null && ordersList.size() > 0){
            for (Orders ordersDB : ordersList) {
                Orders orders = Orders.builder()
                        .id(ordersDB.getId())
                        .status(OrderTransition.PAYMENT_TIMEOUT.getTargetStatus())
                        .cancelReason("Automatically canceled after payment timeout")
                        .cancelTime(LocalDateTime.now())
                        .build();
                // The customer may have paid after the query ran; then the status no longer matches.
                if (orderMapper.updateStatus(orders, Orders.PENDING_PAYMENT) == 0) {
                    log.info("Order {} changed status before the timeout cancellation, skipped", ordersDB.getNumber());
                }
            }
        }
    }

    @Scheduled(cron = "0 0 1 * * ?")
    public void processDeliveryOrder(){
        log.info("Completing orders that have been in delivery for over an hour");

        LocalDateTime time = LocalDateTime.now().plusMinutes(-60);

        List<Orders> ordersList = orderMapper.getByStatusAndOrderTimeLT(Orders.DELIVERY_IN_PROGRESS, time);

        if(ordersList != null && ordersList.size() > 0){
            for (Orders ordersDB : ordersList) {
                Orders orders = Orders.builder()
                        .id(ordersDB.getId())
                        .status(OrderTransition.COMPLETE.getTargetStatus())
                        .build();
                if (orderMapper.updateStatus(orders, Orders.DELIVERY_IN_PROGRESS) == 0) {
                    log.info("Order {} changed status before automatic completion, skipped", ordersDB.getNumber());
                }
            }
        }
    }
}
