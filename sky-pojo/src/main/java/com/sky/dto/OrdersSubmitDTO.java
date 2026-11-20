package com.sky.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * What the customer sends when placing an order. There is deliberately no amount field:
 * the server computes the total from the cart.
 */
@Data
public class OrdersSubmitDTO implements Serializable {

    private Long addressBookId;

    private int payMethod;

    private String remark;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime estimatedDeliveryTime;

    private Integer deliveryStatus;

    private Integer tablewareNumber;

    private Integer tablewareStatus;
}
