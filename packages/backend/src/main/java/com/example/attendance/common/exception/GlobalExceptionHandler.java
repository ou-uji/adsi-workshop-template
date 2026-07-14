package com.example.attendance.common.exception;

import com.example.attendance.common.error.ErrorResponse;
import com.example.attendance.common.error.ErrorResponse.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 例外ハンドリングを集約する共通アドバイス。
 *
 * <p>方針（`.claude/rules/security.md` / `common/coding-style.md`）:</p>
 * <ul>
 *   <li>内部エラーの詳細（スタックトレース・SQL）はクライアントに返さない</li>
 *   <li>バリデーションエラーはフィールド名 + メッセージで返す</li>
 *   <li>サーバー側には詳細をログに残す（スタックトレース含む）</li>
 * </ul>
 *
 * ※ 認証系（401/403）の例外は Spring Security 設定側で扱う想定。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** リソース未存在 → 404 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    /** 業務ルール違反（重複・不正遷移等） → 409 */
    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(
            BusinessRuleViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    /** Bean Validation エラー → 400（フィールド単位で返す） */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "入力内容に誤りがあります", request, fieldErrors);
    }

    /**
     * 認可拒否（メソッドセキュリティの {@code @PreAuthorize} 等）→ 403。
     *
     * <p>{@code AuthorizationDeniedException} は {@link AccessDeniedException} のサブクラス。
     * これを捕まえないと {@link #handleUnexpected} が 500 に変換してしまう。ADMIN 限定 API
     * （Unit A の社員管理・Unit C の休暇承認など）で権限外アクセスを 403 で返すために必要。</p>
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "この操作を行う権限がありません", request, List.of());
    }

    /** 想定外の例外 → 500（詳細はログのみ、クライアントには汎用メッセージ） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "サーバーでエラーが発生しました", request, List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status, String message,
            HttpServletRequest request, List<FieldError> fieldErrors) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(body);
    }
}
