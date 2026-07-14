package com.example.attendance.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class AttendanceRecordRepositoryTest {

    @Autowired
    private AttendanceRecordRepository repository;

    @Test
    @DisplayName("勤怠レコードを保存して取得できる")
    void save_validRecord_persists() {
        var record = new AttendanceRecord(1L, LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 9, 0));
        var saved = repository.save(record);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmployeeId()).isEqualTo(1L);
        assertThat(saved.getWorkDate()).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(saved.getClockInAt()).isEqualTo(LocalDateTime.of(2026, 7, 14, 9, 0));
    }

    @Test
    @DisplayName("社員IDと日付で当日レコードを検索できる")
    void findByEmployeeIdAndWorkDate_exists_returnsRecord() {
        var record = new AttendanceRecord(1L, LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 9, 0));
        repository.save(record);

        Optional<AttendanceRecord> found = repository.findByEmployeeIdAndWorkDate(1L, LocalDate.of(2026, 7, 14));

        assertThat(found).isPresent();
        assertThat(found.get().getEmployeeId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("未打刻の日は空が返る")
    void findByEmployeeIdAndWorkDate_notExists_returnsEmpty() {
        Optional<AttendanceRecord> found = repository.findByEmployeeIdAndWorkDate(1L, LocalDate.of(2026, 7, 14));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("同一社員・同一日の重複保存は一意制約違反")
    void uniqueConstraint_sameEmployeeSameDate_throwsException() {
        var record1 = new AttendanceRecord(1L, LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 9, 0));
        repository.saveAndFlush(record1);

        var record2 = new AttendanceRecord(1L, LocalDate.of(2026, 7, 14), LocalDateTime.of(2026, 7, 14, 10, 0));

        assertThatThrownBy(() -> repository.saveAndFlush(record2))
                .isInstanceOf(Exception.class);
    }
}
