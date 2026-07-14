package com.example.attendance.employee.dto;

import com.example.attendance.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 社員登録リクエスト（ADMIN が新規社員を作る）。
 *
 * <p>イミュータブルな record。バリデーションは境界で行い、失敗は共通ハンドラが
 * fieldErrors（400）で返す。password は平文で受け取り、Service で BCrypt ハッシュ化する
 * （このオブジェクトはログ出力しない）。</p>
 *
 * @param password 初期パスワード。BCrypt はバイト長 72 までのため上限 72。
 */
public record CreateEmployeeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotNull Role role
) {
}
