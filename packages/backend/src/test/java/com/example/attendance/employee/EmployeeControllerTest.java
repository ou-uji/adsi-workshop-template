package com.example.attendance.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import com.example.attendance.common.config.SecurityConfig;
import com.example.attendance.common.enums.Role;
import com.example.attendance.common.exception.BusinessRuleViolationException;
import com.example.attendance.common.exception.ResourceNotFoundException;
import com.example.attendance.employee.dto.EmployeeResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

/**
 * EmployeeController のテスト（HTTP 境界 + 認可）。
 *
 * <p>SecurityConfig を取り込み、認可（ADMIN のみ = メソッドセキュリティ）を実際に効かせる。
 * 認証コンテキストは {@code @WithMockUser} でモック（認証ユニット未完成でも独立に検証可能）。
 * CSRF が現状有効なため、POST/PUT には {@code csrf()} を付ける（認証ユニットで disable されたら不要）。</p>
 */
@WebMvcTest(EmployeeController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class EmployeeControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private EmployeeService service;

    @BeforeEach
    void setUp() {
        // @WithMockUser の認証を MockMvc リクエストへ伝播させるため springSecurity() を明示適用する
        // （SB4 の @WebMvcTest 単独では自動適用されず、全リクエストが匿名になり 403 になる）
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static EmployeeResponse sampleResponse(Long id) {
        return new EmployeeResponse(id, "田中太郎", "tanaka@example.com", Role.MEMBER,
                OffsetDateTime.parse("2026-07-14T10:00:00+09:00"),
                OffsetDateTime.parse("2026-07-14T10:00:00+09:00"));
    }

    @Test
    @DisplayName("登録: ADMIN が正しい body で登録すると 201")
    @WithMockUser(roles = "ADMIN")
    void create_asAdmin_returns201() throws Exception {
        when(service.create(any())).thenReturn(sampleResponse(1L));

        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"田中太郎","email":"tanaka@example.com","password":"password","role":"MEMBER"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.email").value("tanaka@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("登録: 不正な body はフィールド単位の 400")
    @WithMockUser(roles = "ADMIN")
    void create_invalidBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","email":"not-an-email","password":"short","role":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    @DisplayName("登録: email 重複は 409")
    @WithMockUser(roles = "ADMIN")
    void create_duplicateEmail_returns409() throws Exception {
        when(service.create(any()))
                .thenThrow(new BusinessRuleViolationException("このメールアドレスは既に登録されています"));

        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"田中太郎","email":"dup@example.com","password":"password","role":"MEMBER"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("一覧: ADMIN は 200 で配列が返る")
    @WithMockUser(roles = "ADMIN")
    void list_asAdmin_returns200Array() throws Exception {
        when(service.findAll()).thenReturn(List.of(sampleResponse(1L), sampleResponse(2L)));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("取得: 存在する ID は 200")
    @WithMockUser(roles = "ADMIN")
    void getById_existing_returns200() throws Exception {
        when(service.findById(1L)).thenReturn(sampleResponse(1L));

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("取得: 存在しない ID は 404")
    @WithMockUser(roles = "ADMIN")
    void getById_missing_returns404() throws Exception {
        when(service.findById(99L)).thenThrow(new ResourceNotFoundException("社員が見つかりません: id=99"));

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("編集: ADMIN が既存を更新すると 200")
    @WithMockUser(roles = "ADMIN")
    void update_asAdmin_returns200() throws Exception {
        when(service.update(eq(1L), any())).thenReturn(sampleResponse(1L));

        mockMvc.perform(put("/api/employees/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"新名","email":"new@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("編集: 存在しない ID は 404")
    @WithMockUser(roles = "ADMIN")
    void update_missing_returns404() throws Exception {
        when(service.update(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("社員が見つかりません: id=99"));

        mockMvc.perform(put("/api/employees/99").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"新名","email":"new@example.com","role":"ADMIN"}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("認可: MEMBER は登録できない（403）")
    @WithMockUser(roles = "MEMBER")
    void create_asMember_returns403() throws Exception {
        mockMvc.perform(post("/api/employees").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"田中太郎","email":"tanaka@example.com","password":"password","role":"MEMBER"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("認可: MEMBER は一覧を取得できない（403）")
    @WithMockUser(roles = "MEMBER")
    void list_asMember_returns403() throws Exception {
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("認可: 未認証は拒否される（4xx）")
    @WithAnonymousUser
    void list_anonymous_isDenied() throws Exception {
        // 正確な 401/403 は認証ユニットの entry point が決める。ここでは「拒否される」ことのみ検証。
        mockMvc.perform(get("/api/employees"))
                .andExpect(status().is4xxClientError());
    }
}
