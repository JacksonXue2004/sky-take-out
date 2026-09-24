package com.sky.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * C端用户登录（邮箱登录）
 */
@Data
public class UserLoginDTO implements Serializable {

    //邮箱
    private String email;

    //密码
    private String password;

}
