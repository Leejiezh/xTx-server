package com.leejie.xtx.wechat.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import com.leejie.xtx.wechat.service.WechatLoginService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WechatLoginServiceImpl implements WechatLoginService {

    private final WxMaService wxMaService;

    @Override
    public WxLoginResult login(String code) {
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(code);
            return new WxLoginResult(session.getOpenid(), session.getSessionKey(), session.getUnionid());
        } catch (WxErrorException e) {
            // code 失效/已被使用（40029 等）属客户端问题，转 422，避免冒成 500 掩盖原因
            throw new BusinessException(422, "微信登录失败: " + e.getError().getErrorMsg());
        }
    }
}
