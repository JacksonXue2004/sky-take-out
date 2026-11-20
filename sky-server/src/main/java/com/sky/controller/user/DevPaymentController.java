package com.sky.controller.user;

import com.sky.config.PaymentSimulationCondition;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Simulated checkout for demos and development. It exists only when PaymentSimulationCondition
 * allows it: sky.payment.simulation-enabled=true, or the dev profile when that property is unset.
 */
@Conditional(PaymentSimulationCondition.class)
@RestController
@RequestMapping("/user/order")
@Api(tags = "Customer development payment")
@Slf4j
public class DevPaymentController {

    private final OrderService orderService;

    public DevPaymentController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Reuses the existing payment-success workflow, including persistence and
     * merchant WebSocket notification, without contacting a payment provider.
     */
    @PutMapping("/payment/simulate")
    @ApiOperation("Simulate a successful payment in development")
    public Result simulatePayment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) {
        log.info("Simulating successful payment for order: {}", ordersPaymentDTO.getOrderNumber());
        orderService.simulatePayment(ordersPaymentDTO.getOrderNumber());
        return Result.success();
    }
}
