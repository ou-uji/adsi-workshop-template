package com.example.attendance.common.exception;

/**
 * リソースが存在しないときに投げる（HTTP 404 相当）。
 * 例: 存在しない社員 ID での打刻。
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
