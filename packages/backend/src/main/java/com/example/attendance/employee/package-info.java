/**
 * Unit A: ユーザー管理ドメイン（担当: メンバー1 / Flyway V2）。
 *
 * <p>このパッケージに一気通貫で配置する（層でなく機能で縦割り）:</p>
 * <ul>
 *   <li>{@code EmployeeController}  — REST API（@RestController）</li>
 *   <li>{@code EmployeeService} / {@code EmployeeServiceImpl} — ドメインロジック（interface + impl）</li>
 *   <li>{@code EmployeeRepository} — Spring Data JPA（interface）</li>
 *   <li>{@code Employee}           — Entity（id/name/email/passwordHash/role）</li>
 *   <li>{@code dto/…}              — record（CreateEmployeeRequest / EmployeeResponse 等）</li>
 * </ul>
 *
 * <p>Employee は認証(D)と共有する土台のため、Entity とテーブル（Flyway V1）は
 * 共通基盤フェーズで先行して用意する。B/C はこの employeeId を参照する。</p>
 */
package com.example.attendance.employee;
