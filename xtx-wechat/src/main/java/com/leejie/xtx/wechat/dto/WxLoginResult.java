package com.leejie.xtx.wechat.dto;

import lombok.Data;

@Data
public class WxLoginResult {

    private String openid;
    private String sessionKey;
    private String unionid;
    private String errcode;
    private String errmsg;
}