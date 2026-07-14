package com.example.attendance.attendance;

import com.example.attendance.attendance.dto.AttendanceResponse;
import com.example.attendance.common.exception.BusinessRuleViolationException;
import com.example.attendance.common.exception.InvalidStateTransitionException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AttendanceServiceImpl implements AttendanceService {

    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    private final AttendanceRecordRepository repository;

    public AttendanceServiceImpl(AttendanceRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    public AttendanceResponse clockIn(Long employeeId) {
        LocalDate today = LocalDate.now(JST);
        repository.findByEmployeeIdAndWorkDate(employeeId, today)
                .ifPresent(record -> {
                    throw new BusinessRuleViolationException("本日は既に出勤打刻済みです");
                });

        AttendanceRecord record = new AttendanceRecord(employeeId, today, LocalDateTime.now(JST));
        AttendanceRecord saved = repository.save(record);
        return AttendanceResponse.from(saved);
    }

    @Override
    public AttendanceResponse clockOut(Long employeeId) {
        LocalDate today = LocalDate.now(JST);
        AttendanceRecord record = repository.findByEmployeeIdAndWorkDate(employeeId, today)
                .orElseThrow(() -> new InvalidStateTransitionException("出勤打刻がされていません"));

        if (record.getClockOutAt() != null) {
            throw new BusinessRuleViolationException("本日は既に退勤打刻済みです");
        }

        record.clockOut(LocalDateTime.now(JST));
        AttendanceRecord saved = repository.save(record);
        return AttendanceResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public AttendanceResponse getTodayStatus(Long employeeId) {
        LocalDate today = LocalDate.now(JST);
        return repository.findByEmployeeIdAndWorkDate(employeeId, today)
                .map(AttendanceResponse::from)
                .orElse(new AttendanceResponse(null, employeeId, today, null, null, "NOT_CLOCKED"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceResponse> getHistory(Long employeeId) {
        return repository.findByEmployeeIdOrderByWorkDateDesc(employeeId).stream()
                .map(AttendanceResponse::from)
                .toList();
    }
}
