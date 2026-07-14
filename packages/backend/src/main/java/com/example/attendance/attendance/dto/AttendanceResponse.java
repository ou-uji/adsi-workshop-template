package com.example.attendance.attendance.dto;

import com.example.attendance.attendance.AttendanceRecord;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
        Long id,
        Long employeeId,
        LocalDate workDate,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        String status
) {
    public static AttendanceResponse from(AttendanceRecord record) {
        String status;
        if (record.getClockOutAt() != null) {
            status = "CLOCKED_OUT";
        } else if (record.getClockInAt() != null) {
            status = "CLOCKED_IN";
        } else {
            status = "NOT_CLOCKED";
        }
        return new AttendanceResponse(
                record.getId(),
                record.getEmployeeId(),
                record.getWorkDate(),
                record.getClockInAt(),
                record.getClockOutAt(),
                status
        );
    }
}
