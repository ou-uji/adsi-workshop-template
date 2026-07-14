package com.example.attendance.employee;

import com.example.attendance.common.exception.BusinessRuleViolationException;
import com.example.attendance.common.exception.ResourceNotFoundException;
import com.example.attendance.employee.dto.CreateEmployeeRequest;
import com.example.attendance.employee.dto.EmployeeResponse;
import com.example.attendance.employee.dto.UpdateEmployeeRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link EmployeeService} の実装。
 *
 * <p>{@code @Transactional} はこの層に付与する。パスワードは {@link PasswordEncoder} で
 * ハッシュ化してから保存し、平文は保持・ログ出力しない（security.md）。</p>
 */
@Service
@Transactional
public class EmployeeServiceImpl implements EmployeeService {

    private final EmployeeRepository repository;
    private final PasswordEncoder passwordEncoder;

    public EmployeeServiceImpl(EmployeeRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public EmployeeResponse create(CreateEmployeeRequest request) {
        if (repository.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("このメールアドレスは既に登録されています");
        }
        var employee = new Employee(
                request.name(),
                request.email(),
                passwordEncoder.encode(request.password()),
                request.role()
        );
        return EmployeeResponse.from(repository.save(employee));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmployeeResponse> findAll() {
        return repository.findAll().stream()
                .map(EmployeeResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long id) {
        return EmployeeResponse.from(findEmployeeOrThrow(id));
    }

    @Override
    public EmployeeResponse update(Long id, UpdateEmployeeRequest request) {
        var employee = findEmployeeOrThrow(id);
        ensureEmailAvailable(request.email(), id);
        employee.updateProfile(request.name(), request.email(), request.role());
        return EmployeeResponse.from(employee);
    }

    private Employee findEmployeeOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("社員が見つかりません: id=" + id));
    }

    /** email が他社員に使われていないか確認する（自分自身の email は重複扱いしない）。 */
    private void ensureEmailAvailable(String email, Long selfId) {
        repository.findByEmail(email)
                .filter(other -> !other.getId().equals(selfId))
                .ifPresent(other -> {
                    throw new BusinessRuleViolationException("このメールアドレスは既に登録されています");
                });
    }
}
