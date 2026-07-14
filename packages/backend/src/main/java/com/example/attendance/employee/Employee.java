package com.example.attendance.employee;

import com.example.attendance.common.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 社員 Entity（共有の核）。employee テーブル（Flyway V1）に対応する。
 *
 * <p>A/B/C の全 Unit がこの {@code id}（= employeeId）と email(ログイン ID 兼用) を参照する。
 * 認証(D)もこの Entity を使う。DTO には直接使わず record で分離する（Entity を API に返さない）。</p>
 *
 * <p>Lombok は Entity のみ許可（DTO は record）。setter は付けず、生成用コンストラクタで初期化する。</p>
 */
@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt ハッシュ（平文保存・ログ出力禁止）。 */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** 楽観ロック（標準）。 */
    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** 新規社員を生成する（id/version/監査列は永続化時に確定）。 */
    public Employee(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * プロフィール（氏名・email・role）を更新する。
     *
     * <p>passwordHash は変更しない（パスワード変更は Unit A のスコープ外 = YAGNI）。
     * 列・テーブル定義（Flyway V1）は変えず、更新可能な属性のみを対象にする。</p>
     */
    public void updateProfile(String name, String email, Role role) {
        this.name = name;
        this.email = email;
        this.role = role;
    }

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
}
