package com.sky.dto;

import com.sky.entity.Employee;
import com.sky.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Objects that hold passwords are logged in places, so their toString must leave the password out. */
class CredentialToStringTest {

    private static final String PASSWORD = "not-a-real-password";

    @Test
    void passwordsNeverAppearInToString() {
        EmployeeLoginDTO employeeLogin = new EmployeeLoginDTO();
        employeeLogin.setUsername("admin");
        employeeLogin.setPassword(PASSWORD);
        UserLoginDTO userLogin = new UserLoginDTO();
        userLogin.setEmail("alex@example.test");
        userLogin.setPassword(PASSWORD);
        PasswordEditDTO passwordEdit = new PasswordEditDTO();
        passwordEdit.setOldPassword(PASSWORD);
        passwordEdit.setNewPassword(PASSWORD);

        for (Object holder : Arrays.asList(employeeLogin, userLogin, passwordEdit,
                Employee.builder().username("admin").password(PASSWORD).build(),
                User.builder().email("alex@example.test").password(PASSWORD).build())) {
            assertFalse(holder.toString().contains(PASSWORD), holder.getClass().getSimpleName());
        }
        // The other fields are still printed.
        assertTrue(employeeLogin.toString().contains("admin"));
    }
}
