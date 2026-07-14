package com.example.attendance.employee.dto;

import com.example.attendance.common.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 社員編集リクエスト（ADMIN がプロフィールを更新する）。
 *
 * <p>更新対象は氏名・email・role のみ。パスワード変更は Unit A のスコープ外（YAGNI）のため
 * password は含めない。</p>
 */
public record UpdateEmployeeRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotNull Role role
) {
}
