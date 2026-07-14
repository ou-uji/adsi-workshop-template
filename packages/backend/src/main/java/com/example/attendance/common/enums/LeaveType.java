package com.example.attendance.common.enums;

/**
 * 休暇種別。休暇申請ドメイン（leave）で使う共有 Enum。
 *
 * MVP では最小限の種別のみ。必要になったら追加する（YAGNI）。
 */
public enum LeaveType {
    /** 有給休暇 */
    PAID,
    /** 欠勤 */
    ABSENCE,
    /** 特別休暇 */
    SPECIAL
}
