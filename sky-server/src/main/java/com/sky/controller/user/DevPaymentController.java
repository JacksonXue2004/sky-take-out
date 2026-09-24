package com.sky.controller.user;

import com.sky.dto.OrdersPaymentDTO;
import com.sky.result.Result;
import com.sky.service.OrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Portfolio checkout support. This controller is unavailable outside the dev profile.
 */
@Profile("dev")
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
        orderService.paySuccess(ordersPaymentDTO.getOrderNumber());
        return Result.success();
    }
}
