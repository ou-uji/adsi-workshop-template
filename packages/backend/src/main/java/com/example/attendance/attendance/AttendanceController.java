package com.example.attendance.attendance;

import com.example.attendance.attendance.dto.AttendanceResponse;
import com.example.attendance.employee.Employee;
import com.example.attendance.employee.EmployeeRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final EmployeeRepository employeeRepository;

    public AttendanceController(AttendanceService attendanceService,
                                EmployeeRepository employeeRepository) {
        this.attendanceService = attendanceService;
        this.employeeRepository = employeeRepository;
    }

    @PostMapping("/clock-in")
    public ResponseEntity<AttendanceResponse> clockIn(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long employeeId = resolveEmployeeId(userDetails);
        AttendanceResponse response = attendanceService.clockIn(employeeId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/clock-out")
    public ResponseEntity<AttendanceResponse> clockOut(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long employeeId = resolveEmployeeId(userDetails);
        AttendanceResponse response = attendanceService.clockOut(employeeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/today")
    public ResponseEntity<AttendanceResponse> getToday(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long employeeId = resolveEmployeeId(userDetails);
        AttendanceResponse response = attendanceService.getTodayStatus(employeeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AttendanceResponse>> getHistory(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long employeeId = resolveEmployeeId(userDetails);
        List<AttendanceResponse> history = attendanceService.getHistory(employeeId);
        return ResponseEntity.ok(history);
    }

    private Long resolveEmployeeId(UserDetails userDetails) {
        Employee employee = employeeRepository.findByEmail(userDetails.getUsername())
                .orElseThrow();
        return employee.getId();
    }
}
