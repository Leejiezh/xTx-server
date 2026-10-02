package com.leejie.xtx.wechat.dto;

/**
 * 微信 jscode2session 的返回。
 *
 * <p>失败由 WxJava 抛 WxErrorException 表达，故这里只有成功字段。
 */
public record WxLoginResult(String openid, String sessionKey, String unionid) {
}
