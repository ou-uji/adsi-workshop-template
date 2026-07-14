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
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private MockHttpSession login() throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "admin@example.com", "password": "password"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession();
    }

    @Test
    @DisplayName("出勤打刻: 認証済みで200とCLOCKED_INが返る")
    void clockIn_authenticated_returns200() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(post("/api/attendance/clock-in").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_IN"))
                .andExpect(jsonPath("$.clockInAt").exists());
    }

    @Test
    @DisplayName("出勤打刻: 未認証で401が返る")
    void clockIn_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/attendance/clock-in"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("退勤打刻: 出勤後に200とCLOCKED_OUTが返る")
    void clockOut_authenticated_returns200() throws Exception {
        MockHttpSession session = login();

        // 先に出勤
        mockMvc.perform(post("/api/attendance/clock-in").session(session))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/attendance/clock-out").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOCKED_OUT"))
                .andExpect(jsonPath("$.clockOutAt").exists());
    }

    @Test
    @DisplayName("当日状態: 認証済みで200が返る")
    void getToday_authenticated_returns200() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/attendance/today").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    @DisplayName("履歴: 認証済みで200が返る")
    void getHistory_authenticated_returns200() throws Exception {
        MockHttpSession session = login();

        mockMvc.perform(get("/api/attendance").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
