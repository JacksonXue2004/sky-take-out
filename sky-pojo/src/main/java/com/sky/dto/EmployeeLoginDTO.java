package com.sky.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
@ApiModel(description = "Operation")
public class EmployeeLoginDTO implements Serializable {

    @ApiModelProperty("Operation")
    private String username;

    @ApiModelProperty("Operation")
    private String password;

}
