package com.example.attendance.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * ユーザー管理 API の統合テスト（実 H2 + Flyway seed・アプリ全体）。
 *
 * <p>{@code @Transactional} で各テストをロールバックし副作用を残さない。seed は
 * V1.1 で ADMIN 1・MEMBER 2 の計 3 件が入っている前提。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeApiIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EmployeeRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        // @WithMockUser の認証を MockMvc リクエストへ伝播させるため springSecurity() を明示適用する
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("登録→一覧: ADMIN が登録すると一覧に反映される（seed 3 → 4）")
    @WithMockUser(roles = "ADMIN")
    void registerThenList_asAdmin_roundtrip() throws Exception {
        long before = repository.count();

        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"新人 三郎","email":"saburo@example.com","password":"password","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        assertThat(repository.count()).isEqualTo(before + 1);
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                // 実 API の JSON レベルでも passwordHash が漏れないことを確認（型保証の二重チェック）
                .andExpect(jsonPath("$[*].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("登録: パスワードは BCrypt ハッシュで保存され、平文照合できる")
    @WithMockUser(roles = "ADMIN")
    void password_storedAsBcryptHash() throws Exception {
        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType("application/json")
                        .content("""
                                {"name":"検証 太郎","email":"verify@example.com","password":"secret-password","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        var saved = repository.findByEmail("verify@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).isNotEqualTo("secret-password");
        assertThat(passwordEncoder.matches("secret-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("認可: MEMBER は社員 API を使えない（403）")
    @WithMockUser(roles = "MEMBER")
    void employeesApi_asMember_returns403() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isForbidden());
    }
}
