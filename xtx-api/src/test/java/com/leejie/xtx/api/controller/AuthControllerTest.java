package com.leejie.xtx.api.controller;

import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    @DisplayName("POST /auth/login 返回 {code,data:{token,userInfo}} 信封")
    void login_returnsTokenEnvelope() throws Exception {
        AuthService authService = mock(AuthService.class);
        UserVO user = new UserVO();
        user.setId(1L);
        user.setNickname("兔");
        when(authService.login("code-1")).thenReturn(new LoginVO("jwt-token", user));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.userInfo.id").value(1))
                .andExpect(jsonPath("$.data.userInfo.nickname").value("兔"));
    }
}
