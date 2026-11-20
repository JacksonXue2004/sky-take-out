package com.sky.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class PasswordEditDTO implements Serializable {


    private Long empId;


    @ToString.Exclude
    private String oldPassword;


    @ToString.Exclude
    private String newPassword;

}
