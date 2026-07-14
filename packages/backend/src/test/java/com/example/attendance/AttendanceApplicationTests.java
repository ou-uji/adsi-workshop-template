package com.example.attendance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 起動土台の統合テスト（TDD の1本目 = 土台）。
 *
 * <p>Spring コンテキストが起動し、Flyway V1（employee テーブル）が適用できることを確認する。
 * これが通れば「アプリが起動する」土台が成立している。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AttendanceApplicationTests {

    @Test
    @DisplayName("アプリ起動: Spring コンテキストと Flyway V1 の適用が通る")
    void contextLoads() {
        // Spring コンテキストの起動と Flyway マイグレーション適用が成功すれば合格。
    }
}
