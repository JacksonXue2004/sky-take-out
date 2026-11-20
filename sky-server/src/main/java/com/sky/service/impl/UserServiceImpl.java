package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.constant.PasswordConstant;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.User;
import com.sky.exception.AccountNotFoundException;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.UserMapper;
import com.sky.service.UserService;
import com.sky.utils.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    public User login(UserLoginDTO userLoginDTO) {
        String email = userLoginDTO.getEmail();
        String password = userLoginDTO.getPassword();


        User user = userMapper.getByEmail(email);


        if (user == null) {
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }


        if (!PasswordHasher.matches(password, user.getPassword())) {
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }
        // The password was just verified, so an old MD5 hash can be replaced with BCrypt now.
        if (PasswordHasher.needsRehash(user.getPassword())) {
            userMapper.updatePassword(user.getId(), PasswordHasher.hash(password));
        }


        return user;
    }

    public User register(UserLoginDTO userLoginDTO) {
        String email = userLoginDTO.getEmail();
        String password = userLoginDTO.getPassword();


        User existUser = userMapper.getByEmail(email);
        if (existUser != null) {
            throw new AccountNotFoundException(MessageConstant.ALREADY_EXISTS);
        }


        User user = User.builder()
                .email(email)
                .password(PasswordHasher.hash(password))
                .createTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);

        log.info("Application event: {}", email);
        return user;
    }
}