package com.sky.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "Operation")
public class EmployeeLoginVO implements Serializable {

    @ApiModelProperty("Operation")
    private Long id;

    @ApiModelProperty("Operation")
    private String userName;

    @ApiModelProperty("Operation")
    private String name;

    @ApiModelProperty("Operation")
    private String token;

}
