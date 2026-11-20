package com.sky.dto;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
public class UserLoginDTO implements Serializable {


    private String email;


    @ToString.Exclude
    private String password;

}
