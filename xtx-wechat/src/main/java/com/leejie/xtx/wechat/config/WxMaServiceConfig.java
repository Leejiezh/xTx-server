package com.leejie.xtx.wechat.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WxJava 小程序客户端装配。
 *
 * <p>只依赖 weixin-java-miniapp（core），没有引入 wx-java-miniapp-spring-boot-starter，
 * 所以这里手工建 WxMaService 单例，而非依赖 starter 自动配置。
 */
@Configuration
@RequiredArgsConstructor
public class WxMaServiceConfig {

    private final WechatConfig wechatConfig;

    @Bean
    public WxMaService wxMaService() {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(wechatConfig.getAppId());
        config.setSecret(wechatConfig.getAppSecret());

        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
