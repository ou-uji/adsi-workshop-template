package com.example.attendance.auth.dto;

import com.example.attendance.common.enums.Role;
import com.example.attendance.employee.Employee;

public record AuthResponse(
        Long id,
        String name,
        String email,
        Role role
) {
    public static AuthResponse from(Employee employee) {
        return new AuthResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getRole()
        );
    }
}
