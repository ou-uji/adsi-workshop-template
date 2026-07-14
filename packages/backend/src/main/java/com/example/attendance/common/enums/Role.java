package com.example.attendance.common.enums;

/**
 * 社員のロール。認証・認可の判定に使う共有 Enum。
 *
 * <ul>
 *   <li>{@link #ADMIN} — 管理者。休暇の承認/却下ができる。</li>
 *   <li>{@link #MEMBER} — 一般社員。自分の打刻・休暇申請ができる。</li>
 * </ul>
 *
 * 共通基盤（common）に置き、employee / attendance / leave の各ドメインから参照する。
 */
public enum Role {
    ADMIN,
    MEMBER
}
