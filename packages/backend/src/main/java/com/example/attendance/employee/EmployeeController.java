package com.example.attendance.employee;

import com.example.attendance.employee.dto.CreateEmployeeRequest;
import com.example.attendance.employee.dto.EmployeeResponse;
import com.example.attendance.employee.dto.UpdateEmployeeRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ユーザー管理 API（Unit A）。すべて ADMIN のみ（メソッドセキュリティ）。
 *
 * <p>認可は {@link PreAuthorize} で守る（共通の SecurityConfig にパスを足さない）。
 * バリデーション失敗（400）・未存在（404）・重複（409）は共通ハンドラが整形する。</p>
 */
@RestController
@RequestMapping("/api/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    /** 社員を登録する。成功は 201 + Location。 */
    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request) {
        EmployeeResponse created = service.create(request);
        return ResponseEntity
                .created(URI.create("/api/employees/" + created.id()))
                .body(created);
    }

    /** 社員一覧を返す。 */
    @GetMapping
    public List<EmployeeResponse> list() {
        return service.findAll();
    }

    /** 社員 1 件を取得する（編集フォーム初期表示用）。 */
    @GetMapping("/{id}")
    public EmployeeResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    /** 社員のプロフィールを編集する。 */
    @PutMapping("/{id}")
    public EmployeeResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmployeeRequest request) {
        return service.update(id, request);
    }
}
