package com.example.attendance.common.exception;

/**
 * 業務ルール違反（HTTP 409 Conflict 相当）。
 *
 * 例:
 * <ul>
 *   <li>当日 2 回目の出勤打刻</li>
 *   <li>メール重複での社員登録</li>
 *   <li>不正な状態遷移（APPROVED を再承認 等）</li>
 * </ul>
 */
public class BusinessRuleViolationException extends RuntimeException {
    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
