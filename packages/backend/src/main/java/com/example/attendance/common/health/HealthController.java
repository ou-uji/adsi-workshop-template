package com.example.attendance.common.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 起動確認用の最小 API。フロント・プロキシ・監視からの疎通確認に使う（認証不要）。
 *
 * <p>共通 API 規約（設計 §1.3）の型: パスは {@code /api/...}、レスポンスは record DTO。</p>
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public HealthResponse health() {
        return new HealthResponse("ok");
    }

    /** ヘルスチェックのレスポンス（record DTO）。 */
    public record HealthResponse(String status) {
    }
}
