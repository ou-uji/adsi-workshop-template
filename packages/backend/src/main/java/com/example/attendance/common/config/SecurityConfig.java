package com.example.attendance.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 認証（最小構成）の共通設定 — 雛形。
 *
 * <p>方針（`.claude/rules/security.md` / `java-spring-boot.md` SB4 互換）:</p>
 * <ul>
 *   <li>{@code SecurityFilterChain} Bean 方式（{@code WebSecurityConfigurerAdapter} 禁止）</li>
 *   <li>{@code authorizeHttpRequests} / {@code requestMatchers}（旧 API 禁止）</li>
 *   <li>パスワードは {@link BCryptPasswordEncoder} でハッシュ化</li>
 *   <li>デフォルト拒否 + 許可パスをホワイトリスト</li>
 * </ul>
 *
 * <p>⚠️ 雛形段階。ログイン方式（フォーム/セッション）・保護ルート・CORS は
 * design / 実装フェーズで確定する（TODO を参照）。</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // TODO(design): 許可パスのホワイトリストを確定する
                //   例: ログイン API・H2 コンソール（workshop）・静的資産のみ permitAll、他は authenticated
                .requestMatchers("/api/health", "/api/auth/**", "/h2-console/**").permitAll()
                .anyRequest().authenticated()
            );
            // TODO(design): ログイン方式（formLogin / セッション）、ログアウト、CORS、
            //   H2 コンソール表示のための frameOptions 無効化などを実装フェーズで追加する。
        return http.build();
    }
}
