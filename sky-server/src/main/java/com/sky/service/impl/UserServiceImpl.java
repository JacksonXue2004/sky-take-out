package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 邮箱登录
     * @param userLoginDTO
     * @return
     */
    public User login(UserLoginDTO userLoginDTO) {
        String email = userLoginDTO.getEmail();
        String password = userLoginDTO.getPassword();

        //1、根据邮箱查询用户
        User user = userMapper.getByEmail(email);

        //2、判断用户是否存在
        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //3、密码比对（MD5加密）
        String md5Password = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!md5Password.equals(user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //4、返回用户对象
        return user;
    }

    /**
     * 用户注册
     * @param userLoginDTO
     * @return
     */
    public User register(UserLoginDTO userLoginDTO) {
        String email = userLoginDTO.getEmail();
        String password = userLoginDTO.getPassword();

        //1、检查邮箱是否已被注册
        User existUser = userMapper.getByEmail(email);
        if (existUser != null) {
            throw new AccountNotFoundException(MessageConstant.ALREADY_EXISTS);
        }

        //2、创建新用户
        User user = User.builder()
                .email(email)
                .password(DigestUtils.md5DigestAsHex(password.getBytes()))
                .createTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        log.info("用户注册成功：{}", email);
        return user;
    }
}