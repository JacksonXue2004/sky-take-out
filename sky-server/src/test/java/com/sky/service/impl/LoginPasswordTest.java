package com.sky.service.impl;

import com.sky.constant.StatusConstant;
import com.sky.dto.EmployeeLoginDTO;
import com.sky.dto.UserLoginDTO;
import com.sky.entity.Employee;
import com.sky.entity.User;
import com.sky.exception.PasswordErrorException;
import com.sky.mapper.EmployeeMapper;
import com.sky.mapper.UserMapper;
import com.sky.utils.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Logins accept old MD5 hashes once and replace them with BCrypt; new accounts get BCrypt. */
@ExtendWith(MockitoExtension.class)
class LoginPasswordTest {

    private static final String LEGACY_MD5_OF_123456 = "e10adc3949ba59abbe56e057f20f883e";

    @Mock
    private UserMapper userMapper;
    @Mock
    private EmployeeMapper employeeMapper;

    @InjectMocks
    private UserServiceImpl userService;
    @InjectMocks
    private EmployeeServiceImpl employeeService;

    @Test
    void aCustomerWithAnOldMd5HashLogsInAndGetsABcryptHash() {
        when(userMapper.getByEmail("alex@example.test"))
                .thenReturn(User.builder().id(7L).email("alex@example.test").password(LEGACY_MD5_OF_123456).build());

        userService.login(userLogin("123456"));

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(userMapper).updatePassword(eq(7L), newHash.capture());
        assertTrue(newHash.getValue().startsWith("$2a$"));
        assertTrue(PasswordHasher.matches("123456", newHash.getValue()));
    }

    @Test
    void aCustomerWithABcryptHashIsNotRehashed() {
        when(userMapper.getByEmail("alex@example.test"))
                .thenReturn(User.builder().id(7L).password(PasswordHasher.hash("123456")).build());

        userService.login(userLogin("123456"));

        verify(userMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void aWrongPasswordIsRefusedAndNothingIsRewritten() {
        when(userMapper.getByEmail("alex@example.test"))
                .thenReturn(User.builder().id(7L).password(LEGACY_MD5_OF_123456).build());

        assertThrows(PasswordErrorException.class, () -> userService.login(userLogin("not-the-password")));
        verify(userMapper, never()).updatePassword(anyLong(), anyString());
    }

    @Test
    void newCustomersAreStoredWithBcrypt() {
        UserLoginDTO registration = userLogin("a long new password");

        userService.register(registration);

        verify(userMapper).insert(argThat(user -> user.getPassword().startsWith("$2a$")
                && PasswordHasher.matches("a long new password", user.getPassword())));
    }

    @Test
    void theDemoAdminsOldHashIsUpgradedOnFirstLogin() {
        when(employeeMapper.getByUsername("admin")).thenReturn(Employee.builder()
                .id(1L).username("admin").password(LEGACY_MD5_OF_123456).status(StatusConstant.ENABLE).build());
        EmployeeLoginDTO login = new EmployeeLoginDTO();
        login.setUsername("admin");
        login.setPassword("123456");

        employeeService.login(login);

        verify(employeeMapper).updatePassword(eq(1L), argThat(hash -> PasswordHasher.matches("123456", hash)));
        verify(employeeMapper, never()).update(any());
    }

    private static UserLoginDTO userLogin(String password) {
        UserLoginDTO dto = new UserLoginDTO();
        dto.setEmail("alex@example.test");
        dto.setPassword(password);
        return dto;
    }
}
