package com.example.attendance.employee;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Employee のデータアクセス（Spring Data JPA / DAO は使わない）。
 *
 * <p>共通基盤で固定するつなぎ目。認証(D) と Unit A/B/C がこの型を参照する。
 * email はログイン ID 兼用のため {@link #findByEmail(String)} を提供する。</p>
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /** ログイン・重複チェック用。email は一意。 */
    Optional<Employee> findByEmail(String email);

    /** 登録時の重複判定用。 */
    boolean existsByEmail(String email);
}
