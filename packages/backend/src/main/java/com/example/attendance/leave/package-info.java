/**
 * Unit C: 休暇申請 + 承認ドメイン（担当: 経験者 or ペア / Flyway V4）。
 *
 * <p>このパッケージに一気通貫で配置する:</p>
 * <ul>
 *   <li>{@code LeaveController}  — 申請・一覧・承認/却下の REST API</li>
 *   <li>{@code LeaveService} / {@code LeaveServiceImpl}</li>
 *   <li>{@code LeaveRepository} — Spring Data JPA</li>
 *   <li>{@code LeaveRequest}    — Entity（employeeId/type/startDate/endDate/reason/status）</li>
 *   <li>{@code dto/…}           — record</li>
 * </ul>
 *
 * <p>状態遷移 PENDING → APPROVED/REJECTED を持つ（承認/却下は ADMIN のみ）。
 * 不正な再遷移は {@code BusinessRuleViolationException}（409）。最重量 Unit。</p>
 */
package com.example.attendance.leave;
