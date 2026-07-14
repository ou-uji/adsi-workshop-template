package com.example.attendance.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.example.attendance.attendance.dto.AttendanceResponse;
import com.example.attendance.common.exception.BusinessRuleViolationException;
import com.example.attendance.common.exception.InvalidStateTransitionException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceImplTest {

    @Mock
    private AttendanceRecordRepository repository;

    private AttendanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AttendanceServiceImpl(repository);
    }

    @Test
    @DisplayName("出勤打刻: 当日未打刻なら出勤レコードが作成される")
    void clockIn_firstToday_createsRecord() {
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = service.clockIn(1L);

        assertThat(response.employeeId()).isEqualTo(1L);
        assertThat(response.workDate()).isEqualTo(LocalDate.now());
        assertThat(response.clockInAt()).isNotNull();
        assertThat(response.status()).isEqualTo("CLOCKED_IN");
    }

    @Test
    @DisplayName("出勤打刻: 当日既に出勤済みなら409エラー")
    void clockIn_alreadyClockedIn_throwsConflict() {
        var existing = new AttendanceRecord(1L, LocalDate.now(), LocalDateTime.now());
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.clockIn(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("本日は既に出勤打刻済みです");
    }

    @Test
    @DisplayName("退勤打刻: 出勤済みなら退勤時刻が記録される")
    void clockOut_afterClockIn_recordsTime() {
        var existing = new AttendanceRecord(1L, LocalDate.now(), LocalDateTime.now().minusHours(8));
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AttendanceResponse response = service.clockOut(1L);

        assertThat(response.clockOutAt()).isNotNull();
        assertThat(response.status()).isEqualTo("CLOCKED_OUT");
    }

    @Test
    @DisplayName("退勤打刻: 未出勤なら400エラー")
    void clockOut_notClockedIn_throwsBadRequest() {
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.clockOut(1L))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessage("出勤打刻がされていません");
    }

    @Test
    @DisplayName("退勤打刻: 既に退勤済みなら409エラー")
    void clockOut_alreadyClockedOut_throwsConflict() {
        var existing = new AttendanceRecord(1L, LocalDate.now(), LocalDateTime.now().minusHours(8));
        existing.clockOut(LocalDateTime.now().minusHours(1));
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.clockOut(1L))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("本日は既に退勤打刻済みです");
    }

    @Test
    @DisplayName("当日状態: 未打刻ならNOT_CLOCKED")
    void getTodayStatus_noClock_returnsNotClocked() {
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.empty());

        AttendanceResponse response = service.getTodayStatus(1L);

        assertThat(response.status()).isEqualTo("NOT_CLOCKED");
        assertThat(response.clockInAt()).isNull();
    }

    @Test
    @DisplayName("当日状態: 出勤済みならCLOCKED_IN")
    void getTodayStatus_clockedIn_returnsClockedIn() {
        var existing = new AttendanceRecord(1L, LocalDate.now(), LocalDateTime.now());
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(existing));

        AttendanceResponse response = service.getTodayStatus(1L);

        assertThat(response.status()).isEqualTo("CLOCKED_IN");
    }

    @Test
    @DisplayName("当日状態: 退勤済みならCLOCKED_OUT")
    void getTodayStatus_clockedOut_returnsClockedOut() {
        var existing = new AttendanceRecord(1L, LocalDate.now(), LocalDateTime.now().minusHours(8));
        existing.clockOut(LocalDateTime.now());
        when(repository.findByEmployeeIdAndWorkDate(any(), any())).thenReturn(Optional.of(existing));

        AttendanceResponse response = service.getTodayStatus(1L);

        assertThat(response.status()).isEqualTo("CLOCKED_OUT");
    }

    @Test
    @DisplayName("履歴: 日付降順のリストが返る")
    void getHistory_returnsListOrderedByDate() {
        var record1 = new AttendanceRecord(1L, LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 9, 0));
        var record2 = new AttendanceRecord(1L, LocalDate.of(2026, 7, 13), LocalDateTime.of(2026, 7, 13, 9, 0));
        when(repository.findByEmployeeIdOrderByWorkDateDesc(1L)).thenReturn(List.of(record1, record2));

        List<AttendanceResponse> history = service.getHistory(1L);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).workDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(history.get(1).workDate()).isEqualTo(LocalDate.of(2026, 7, 13));
    }
}
