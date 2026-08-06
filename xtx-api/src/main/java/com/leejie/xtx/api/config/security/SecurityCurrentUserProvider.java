package com.leejie.xtx.api.config.security;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import org.springframework.stereotype.Component;

/**
 * {@link CurrentUserProvider} 的生产适配器 —— 从 Spring Security 上下文取用户 ID。
 *
 * <p>本类只是把已有的 {@link SecurityUtils} 静态方法接到接缝上，故意保持一行实现：
 * 401 的判定逻辑留在 SecurityUtils，不在这里重复。
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long currentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
