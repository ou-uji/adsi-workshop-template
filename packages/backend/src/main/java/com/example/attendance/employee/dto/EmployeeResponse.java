package com.example.attendance.employee.dto;

import com.example.attendance.common.enums.Role;
import com.example.attendance.employee.Employee;
import java.time.OffsetDateTime;

/**
 * 社員レスポンス（API 応答）。
 *
 * <p>Entity を直接返さないための record。<b>passwordHash は含めない</b>（漏洩防止 / security.md）。
 * マッピングは {@link #from(Employee)} に集約する（MapStruct 禁止・手動マッピング）。</p>
 */
public record EmployeeResponse(
        Long id,
        String name,
        String email,
        Role role,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Entity → レスポンスへ変換する。passwordHash は意図的に含めない。 */
    public static EmployeeResponse from(Employee employee) {
        return new EmployeeResponse(
                employee.getId(),
                employee.getName(),
                employee.getEmail(),
                employee.getRole(),
                employee.getCreatedAt(),
                employee.getUpdatedAt()
        );
    }
}
