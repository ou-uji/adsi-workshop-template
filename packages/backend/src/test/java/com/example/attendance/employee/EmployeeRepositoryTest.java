package com.example.attendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.attendance.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/**
 * Employee Repository のテスト（共有の核 = employee テーブルの土台）。
 *
 * <p>共通基盤で固定するつなぎ目: 保存/取得・email 一意制約・findByEmail。
 * A/B/C はこの Repository と employee(id) を参照する。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE) // スキーマは Flyway 管理（ddl-auto 禁止）。組込 DB 置換を無効化し test プロファイルの H2 + Flyway を使う
@ActiveProfiles("test")
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository repository;

    @Test
    @DisplayName("保存と取得: 社員を保存すると id が採番され email で取得できる")
    void save_newEmployee_isRetrievableByEmail() {
        // Arrange
        var employee = new Employee("田中太郎", "tanaka@example.com", "hash", Role.MEMBER);

        // Act
        var saved = repository.save(employee);

        // Assert
        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findByEmail("tanaka@example.com")).isPresent();
    }

    @Test
    @DisplayName("email 一意制約: 同じ email で2件保存すると制約違反になる")
    void save_duplicateEmail_throwsConstraintViolation() {
        // Arrange
        repository.saveAndFlush(new Employee("一人目", "dup@example.com", "hash", Role.MEMBER));

        // Act / Assert
        assertThatThrownBy(() ->
                repository.saveAndFlush(new Employee("二人目", "dup@example.com", "hash", Role.MEMBER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByEmail: 存在しない email では empty が返る")
    void findByEmail_nonExisting_returnsEmpty() {
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }
}
