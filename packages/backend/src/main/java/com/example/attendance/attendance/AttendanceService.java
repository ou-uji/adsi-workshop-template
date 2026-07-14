package com.example.attendance.attendance;

import com.example.attendance.attendance.dto.AttendanceResponse;
import java.util.List;

public interface AttendanceService {

    AttendanceResponse clockIn(Long employeeId);

    AttendanceResponse clockOut(Long employeeId);

    AttendanceResponse getTodayStatus(Long employeeId);

    List<AttendanceResponse> getHistory(Long employeeId);
}
