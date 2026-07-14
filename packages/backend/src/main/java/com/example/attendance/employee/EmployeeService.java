package com.example.attendance.employee;

import com.example.attendance.employee.dto.CreateEmployeeRequest;
import com.example.attendance.employee.dto.EmployeeResponse;
import com.example.attendance.employee.dto.UpdateEmployeeRequest;
import java.util.List;

/**
 * ユーザー管理（Unit A）のドメインロジック。interface + impl（impl に {@code @Transactional}）。
 *
 * <p>認可（ADMIN のみ）は Controller のメソッドセキュリティで守る。Service は業務ルール
 * （email 重複・未存在）に専念し、違反は共通例外（→ 共通ハンドラで HTTP 化）で表現する。</p>
 */
public interface EmployeeService {

    /** 社員を登録する。email 重複は業務ルール違反（409）。password は BCrypt ハッシュ化して保存。 */
    EmployeeResponse create(CreateEmployeeRequest request);

    /** 社員一覧を返す。 */
    List<EmployeeResponse> findAll();

    /** ID で 1 件取得する。未存在は 404。 */
    EmployeeResponse findById(Long id);

    /** 社員のプロフィール（氏名・email・role）を更新する。未存在は 404、他者との email 重複は 409。 */
    EmployeeResponse update(Long id, UpdateEmployeeRequest request);
}
