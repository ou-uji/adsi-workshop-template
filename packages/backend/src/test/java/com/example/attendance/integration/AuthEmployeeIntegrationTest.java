package com.example.attendance.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Unit A（社員管理）× Unit D（認証）結合テスト。
 *
 * <p>実際のログインでセッションを確立し、社員 CRUD API を通しで叩く。
 * seed データ: admin@example.com (ADMIN), hanako@example.com (MEMBER)。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthEmployeeIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // --- ヘルパー ---

    private MockHttpSession loginAs(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    // === 正常系: ADMIN ログイン → 社員 CRUD ===

    @Test
    @DisplayName("ADMIN ログイン → 社員登録 → 一覧に反映 → 取得 → 編集 の通しフロー")
    void adminLogin_employeeCrud_fullFlow() throws Exception {
        // 1. ADMIN でログイン
        var session = loginAs("admin@example.com", "password");

        // 2. 社員を登録
        var createResult = mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"結合 テスト","email":"it-test@example.com","password":"secure123","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("結合 テスト"))
                .andExpect(jsonPath("$.email").value("it-test@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();

        // レスポンスから ID を取得
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
        long newId = created.get("id").asLong();
        assertThat(newId).isPositive();

        // 3. 一覧に含まれることを確認
        mockMvc.perform(get("/api/employees").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email == 'it-test@example.com')].name")
                        .value("結合 テスト"));

        // 4. ID で取得
        mockMvc.perform(get("/api/employees/" + newId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("結合 テスト"));

        // 5. 編集
        mockMvc.perform(put("/api/employees/" + newId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"結合 更新済","email":"it-updated@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("結合 更新済"))
                .andExpect(jsonPath("$.email").value("it-updated@example.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    // === 認可: MEMBER はアクセス拒否 ===

    @Test
    @DisplayName("MEMBER ログイン → 社員一覧にアクセス → 403")
    void memberLogin_employeeList_returns403() throws Exception {
        var session = loginAs("hanako@example.com", "password");

        mockMvc.perform(get("/api/employees").session(session))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("MEMBER ログイン → 社員登録 → 403")
    void memberLogin_createEmployee_returns403() throws Exception {
        var session = loginAs("hanako@example.com", "password");

        mockMvc.perform(post("/api/employees")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"不正 登録","email":"bad@example.com","password":"pass","role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());
    }

    // === 未ログイン: 401 ===

    @Test
    @DisplayName("未ログインで社員 API にアクセス → 401")
    void noLogin_employeeApi_returns401() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    // === ログアウト後: 401 ===

    @Test
    @DisplayName("ログアウト後に社員 API にアクセス → 401")
    void afterLogout_employeeApi_returns401() throws Exception {
        var session = loginAs("admin@example.com", "password");

        // ログアウト
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        // ログアウト後のセッションではアクセス不可
        mockMvc.perform(get("/api/employees").session(session))
                .andExpect(status().isUnauthorized());
    }

    // === 不正ログイン ===

    @Test
    @DisplayName("不正なパスワードでログイン → 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@example.com", "password": "wrongpass"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("メールまたはパスワードが正しくありません"));
    }

    // === 登録した社員でログインできる ===

    @Test
    @DisplayName("ADMIN が登録した社員で新たにログインできる")
    void adminCreatesEmployee_newEmployeeCanLogin() throws Exception {
        // ADMIN でログインして社員を作る
        var adminSession = loginAs("admin@example.com", "password");

        mockMvc.perform(post("/api/employees")
                        .session(adminSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"新規 ユーザー","email":"newuser@example.com","password":"mypass123","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated());

        // 作った社員でログインできることを確認
        var newSession = loginAs("newuser@example.com", "mypass123");

        // /me で自分の情報を取得できる
        mockMvc.perform(get("/api/auth/me").session(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("新規 ユーザー"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }
}
