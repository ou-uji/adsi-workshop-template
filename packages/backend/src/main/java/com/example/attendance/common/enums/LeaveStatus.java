package com.example.attendance.common.enums;

/**
 * 休暇申請のステータス（状態遷移を持つ）。
 *
 * <pre>
 *   PENDING ──承認(ADMIN)──▶ APPROVED
 *      │
 *      └────却下(ADMIN)────▶ REJECTED
 * </pre>
 *
 * APPROVED / REJECTED からの再遷移は不正（common の例外で弾く想定）。
 */
public enum LeaveStatus {
    /** 申請中（初期状態） */
    PENDING,
    /** 承認済み */
    APPROVED,
    /** 却下済み */
    REJECTED
}
