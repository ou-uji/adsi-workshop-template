package com.example.attendance.auth;

import com.example.attendance.common.enums.Role;
import com.example.attendance.employee.Employee;
import com.example.attendance.employee.EmployeeRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private CustomUserDetailsService service;

    @Test
    @DisplayName("存在するメールアドレスでUserDetailsが返される")
    void loadUserByUsername_existingEmail_returnsUserDetails() {
        Employee employee = new Employee("管理者太郎", "admin@example.com",
                "$2a$10$hashedpassword", Role.ADMIN);
        when(employeeRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(employee));

        UserDetails result = service.loadUserByUsername("admin@example.com");

        assertThat(result.getUsername()).isEqualTo("admin@example.com");
        assertThat(result.getPassword()).isEqualTo("$2a$10$hashedpassword");
        assertThat(result.getAuthorities())
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("存在しないメールアドレスでUsernameNotFoundExceptionが投げられる")
    void loadUserByUsername_notFound_throwsException() {
        when(employeeRepository.findByEmail("notfound@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("notfound@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
