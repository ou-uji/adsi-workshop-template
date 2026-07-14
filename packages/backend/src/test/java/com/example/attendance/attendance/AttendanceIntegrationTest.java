package com.example.attendance.attendance;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttendanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession login(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    @DisplayName("通しフロー: ログイン→出勤→退勤→履歴確認")
    void fullFlow_login_clockIn_clockOut_history() throws Exception {
        MockHttpSession session = login("hanako@example.com");

        // 当日状態: 未打刻
        mockMvc.perform(get("/api/attendance/today").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_CLOCKED"));

        // 出勤打刻
        mockMvc.perform(post("/api/attendance/clock-in").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_IN"))
                .andExpect(jsonPath("$.clockInAt").exists());

        // 当日状態: 出勤中
        mockMvc.perform(get("/api/attendance/today").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_IN"));

        // 二重出勤防止
        mockMvc.perform(post("/api/attendance/clock-in").session(session))
                .andExpect(status().isConflict());

        // 退勤打刻
        mockMvc.perform(post("/api/attendance/clock-out").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_OUT"))
                .andExpect(jsonPath("$.clockOutAt").exists());

        // 当日状態: 退勤済
        mockMvc.perform(get("/api/attendance/today").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_OUT"));

        // 二重退勤防止
        mockMvc.perform(post("/api/attendance/clock-out").session(session))
                .andExpect(status().isConflict());

        // 履歴取得
        mockMvc.perform(get("/api/attendance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CLOCKED_OUT"));
    }

    @Test
    @DisplayName("未ログインで打刻すると401")
    void clockIn_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/attendance/clock-in"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未出勤で退勤すると400")
    void clockOut_withoutClockIn_returns400() throws Exception {
        MockHttpSession session = login("jiro@example.com");

        mockMvc.perform(post("/api/attendance/clock-out").session(session))
                .andExpect(status().isBadRequest());
    }
}
