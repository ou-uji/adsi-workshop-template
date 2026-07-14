package com.example.attendance.common.error;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * API 共通のエラーレスポンス形式（イミュータブルな record）。
 *
 * <p>内部エラーの詳細（スタックトレース・SQL 等）はクライアントに返さない。
 * バリデーションエラーは {@link FieldError} でフィールド名 + メッセージを返す。</p>
 *
 * @param timestamp 発生時刻
 * @param status    HTTP ステータスコード
 * @param error     ステータスの短い説明（例: "Bad Request"）
 * @param message   クライアント向けの汎用メッセージ
 * @param path      リクエストパス
 * @param fieldErrors フィールド単位のバリデーションエラー（無ければ空）
 */
public record ErrorResponse(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public ErrorResponse {
        // 防御コピー（イミュータビリティ保証）
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** フィールド単位のバリデーションエラー。 */
    public record FieldError(String field, String message) {
    }
}
