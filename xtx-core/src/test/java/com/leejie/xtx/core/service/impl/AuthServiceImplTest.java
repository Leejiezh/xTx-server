package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import com.leejie.xtx.wechat.service.WechatLoginService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private WechatLoginService wechatLoginService;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(wechatLoginService, userMapper);
    }

    @Test
    @DisplayName("老用户：复用已有行不插入，token 里的 userId 与库一致")
    void login_existingUser_reusesRow() {
        User existing = new User();
        existing.setId(7L);
        existing.setOpenid("o-1");
        existing.setNickname("兔兔");
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-1", "sk", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        LoginVO vo = authService.login("c");

        assertEquals(7L, vo.userInfo().getId());
        assertEquals("兔兔", vo.userInfo().getNickname());
        assertNotNull(vo.token());
        // 必须写 any(User.class)：BaseMapper 同时有 insert(T) 与 insert(Collection<T>)，裸 any() 会歧义
        verify(userMapper, never()).insert(any(User.class));

        Claims claims = JwtUtils.parse(vo.token());
        assertEquals(7L, ((Number) claims.get(Constants.CLAIM_USER_ID)).longValue());
        assertEquals("o-1", claims.get(Constants.CLAIM_OPENID));
    }

    @Test
    @DisplayName("首登：插入 user 并回填自增 id，返回该 id 的 token")
    void login_newUser_createsRow() {
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-2", "sk", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L); // 模拟 MySQL 自增主键回填
            return 1;
        });

        LoginVO vo = authService.login("c");

        assertEquals(42L, vo.userInfo().getId());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        assertEquals("o-2", cap.getValue().getOpenid());

        Claims claims = JwtUtils.parse(vo.token());
        assertEquals(42L, ((Number) claims.get(Constants.CLAIM_USER_ID)).longValue());
    }

    @Test
    @DisplayName("并发首登撞唯一键：回读赢家行，不抛异常")
    void login_concurrentFirstLogin_fallsBackToExistingRow() {
        User winner = new User();
        winner.setId(9L);
        winner.setOpenid("o-3");
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-3", "sk", null));
        // 首次查询还没有行；插入撞 uk_openid；回读时拿到赢家那行
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null, winner);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_openid"));

        LoginVO vo = authService.login("c");

        assertEquals(9L, vo.userInfo().getId());
    }
}
