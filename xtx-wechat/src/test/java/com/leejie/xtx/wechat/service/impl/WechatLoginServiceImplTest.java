package com.leejie.xtx.wechat.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatLoginServiceImplTest {

    @Mock
    private WxMaService wxMaService;
    @Mock
    private WxMaUserService wxMaUserService;

    private WechatLoginServiceImpl wechatLoginService;

    @BeforeEach
    void setUp() {
        wechatLoginService = new WechatLoginServiceImpl(wxMaService);
    }

    @Test
    @DisplayName("登录成功：透传 openid / sessionKey / unionid")
    void login_mapsSessionFields() throws Exception {
        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("o-abc");
        session.setSessionKey("sk-123");
        session.setUnionid("u-xyz");
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("code-1")).thenReturn(session);

        WxLoginResult result = wechatLoginService.login("code-1");

        assertEquals("o-abc", result.openid());
        assertEquals("sk-123", result.sessionKey());
        assertEquals("u-xyz", result.unionid());
    }

    @Test
    @DisplayName("微信返回 errcode：转成 422 业务异常，不让它冒成 500")
    void login_translatesWxError() throws Exception {
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("bad"))
                .thenThrow(new WxErrorException(new WxError(40029, "invalid code")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wechatLoginService.login("bad"));

        assertEquals(422, ex.getCode());
        assertTrue(ex.getMessage().contains("invalid code"));
    }
}
