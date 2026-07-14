package com.example.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 勤怠管理システムのエントリポイント。
 *
 * <p>パッケージ構成（ドメイン縦割り = 機能で分担）:</p>
 * <ul>
 *   <li>{@code common}     — 共通基盤（例外ハンドリング・エラー形式・Enum・認証土台）</li>
 *   <li>{@code employee}   — Unit A: ユーザー管理</li>
 *   <li>{@code attendance} — Unit B: 勤怠打刻</li>
 *   <li>{@code leave}      — Unit C: 休暇申請 + 承認</li>
 * </ul>
 */
@SpringBootApplication
public class AttendanceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }
}
