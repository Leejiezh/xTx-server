package com.leejie.xtx.wechat.service;

import com.leejie.xtx.wechat.dto.WxLoginResult;

public interface WechatLoginService {

    /**
     * 微信小程序登录
     *
     * @param code 前端 wx.login() 获取的临时 code
     * @return 登录结果，包含 openid、session_key 等
     */
    WxLoginResult login(String code);
}