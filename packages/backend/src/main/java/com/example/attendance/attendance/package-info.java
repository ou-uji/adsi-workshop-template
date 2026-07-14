/**
 * Unit B: 勤怠打刻ドメイン（担当: メンバー2 / Flyway V3）。
 *
 * <p>このパッケージに一気通貫で配置する:</p>
 * <ul>
 *   <li>{@code AttendanceController} — 出勤/退勤打刻・履歴・当日状態の REST API</li>
 *   <li>{@code AttendanceService} / {@code AttendanceServiceImpl}</li>
 *   <li>{@code AttendanceRepository} — Spring Data JPA</li>
 *   <li>{@code AttendanceRecord}     — Entity（employeeId/workDate/clockInAt/clockOutAt）</li>
 *   <li>{@code dto/…}                — record</li>
 * </ul>
 *
 * <p>業務ルール例: 当日未打刻のみ出勤可（2回目は 409）、出勤済のみ退勤可、
 * 打刻時刻はサーバー現在時刻。employeeId で {@code employee} に依存。</p>
 */
package com.example.attendance.attendance;
