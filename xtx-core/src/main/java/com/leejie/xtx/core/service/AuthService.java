package com.leejie.xtx.core.service;

import com.leejie.xtx.core.dto.LoginVO;

public interface AuthService {

    /**
     * 微信小程序登录：code → openid → 查/建 user → 签发 JWT。
     *
     * @param code 前端 wx.login() 获取的临时 code
     */
    LoginVO login(String code);
}
