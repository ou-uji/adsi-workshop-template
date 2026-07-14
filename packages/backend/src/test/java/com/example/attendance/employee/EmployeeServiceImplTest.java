package com.example.attendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.attendance.common.enums.Role;
import com.example.attendance.common.exception.BusinessRuleViolationException;
import com.example.attendance.common.exception.ResourceNotFoundException;
import com.example.attendance.employee.dto.CreateEmployeeRequest;
import com.example.attendance.employee.dto.EmployeeResponse;
import com.example.attendance.employee.dto.UpdateEmployeeRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * EmployeeService のユニットテスト（Spring コンテキストを起動しない）。
 *
 * <p>Repository と PasswordEncoder をモックし、業務ルール（重複=409 / 未存在=404）と
 * パスワードのハッシュ化・passwordHash 非漏洩を検証する。</p>
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceImplTest {

    @Mock
    private EmployeeRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private EmployeeServiceImpl service;

    private static Employee employeeWithId(Long id, String name, String email, Role role) {
        var employee = new Employee(name, email, "hashed", role);
        // id はテストのため反射で設定（Entity に setter を付けない方針のため）
        try {
            var field = Employee.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(employee, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return employee;
    }

    @Test
    @DisplayName("登録: password を BCrypt ハッシュ化して保存する")
    void create_validRequest_hashesPasswordAndSaves() {
        // Arrange
        var request = new CreateEmployeeRequest("田中太郎", "tanaka@example.com", "password", Role.MEMBER);
        when(repository.existsByEmail("tanaka@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("$2a$10$hashed");
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.create(request);

        // Assert
        var captor = ArgumentCaptor.forClass(Employee.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashed");
    }

    @Test
    @DisplayName("登録: 平文パスワードをそのまま保存しない")
    void create_neverStoresPlaintextPassword() {
        // Arrange
        var request = new CreateEmployeeRequest("田中太郎", "tanaka@example.com", "password", Role.MEMBER);
        when(repository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("$2a$10$hashed");
        when(repository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        service.create(request);

        // Assert
        var captor = ArgumentCaptor.forClass(Employee.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("password");
    }

    @Test
    @DisplayName("登録: email が重複していると業務ルール違反(409)")
    void create_duplicateEmail_throwsBusinessRuleViolation() {
        // Arrange
        var request = new CreateEmployeeRequest("田中太郎", "dup@example.com", "password", Role.MEMBER);
        when(repository.existsByEmail("dup@example.com")).thenReturn(true);

        // Act / Assert
        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessRuleViolationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("一覧: 全件を EmployeeResponse に写像する")
    void findAll_mapsToResponses() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(
                employeeWithId(1L, "太郎", "taro@example.com", Role.ADMIN),
                employeeWithId(2L, "花子", "hanako@example.com", Role.MEMBER)
        ));

        // Act
        List<EmployeeResponse> result = service.findAll();

        // Assert
        assertThat(result).extracting(EmployeeResponse::email)
                .containsExactly("taro@example.com", "hanako@example.com");
    }

    @Test
    @DisplayName("取得: 存在する ID で EmployeeResponse が返る")
    void findById_existingId_returnsResponse() {
        // Arrange
        when(repository.findById(1L))
                .thenReturn(Optional.of(employeeWithId(1L, "太郎", "taro@example.com", Role.ADMIN)));

        // Act
        var result = service.findById(1L);

        // Assert
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("taro@example.com");
    }

    @Test
    @DisplayName("取得: 存在しない ID は 404")
    void findById_nonExisting_throwsResourceNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("編集: 氏名・email・role を更新する")
    void update_existingId_updatesNameEmailRole() {
        // Arrange
        var existing = employeeWithId(1L, "旧名", "old@example.com", Role.MEMBER);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        var request = new UpdateEmployeeRequest("新名", "new@example.com", Role.ADMIN);

        // Act
        var result = service.update(1L, request);

        // Assert
        assertThat(result.name()).isEqualTo("新名");
        assertThat(result.email()).isEqualTo("new@example.com");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("編集: 存在しない ID は 404")
    void update_nonExisting_throwsResourceNotFound() {
        // Arrange
        when(repository.findById(99L)).thenReturn(Optional.empty());
        var request = new UpdateEmployeeRequest("新名", "new@example.com", Role.ADMIN);

        // Act / Assert
        assertThatThrownBy(() -> service.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("編集: email が他社員と重複すると 409")
    void update_emailTakenByAnother_throwsBusinessRuleViolation() {
        // Arrange
        var target = employeeWithId(1L, "対象", "target@example.com", Role.MEMBER);
        var another = employeeWithId(2L, "別人", "taken@example.com", Role.MEMBER);
        when(repository.findById(1L)).thenReturn(Optional.of(target));
        when(repository.findByEmail("taken@example.com")).thenReturn(Optional.of(another));
        var request = new UpdateEmployeeRequest("対象", "taken@example.com", Role.MEMBER);

        // Act / Assert
        assertThatThrownBy(() -> service.update(1L, request))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    @DisplayName("編集: 自分自身の email を指定しても重複扱いしない")
    void update_sameEmailUnchanged_succeeds() {
        // Arrange
        var target = employeeWithId(1L, "対象", "self@example.com", Role.MEMBER);
        when(repository.findById(1L)).thenReturn(Optional.of(target));
        when(repository.findByEmail("self@example.com")).thenReturn(Optional.of(target));
        var request = new UpdateEmployeeRequest("対象（改名）", "self@example.com", Role.ADMIN);

        // Act
        var result = service.update(1L, request);

        // Assert
        assertThat(result.name()).isEqualTo("対象（改名）");
        assertThat(result.role()).isEqualTo(Role.ADMIN);
    }
}
