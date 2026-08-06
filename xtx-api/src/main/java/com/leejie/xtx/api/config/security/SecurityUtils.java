package com.leejie.xtx.api.config.security;

import com.leejie.xtx.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 *
 * <p>内容取自 plan.md Task 1 Step 2。Task 1 尚未实施(SecurityConfig /
 * JwtAuthFilter 还没建)，这里先落地本类以便基类编译通过；
 * 实施 Task 1 时应以本文件为准，不要重复创建。
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 取当前登录用户 ID。
     *
     * <p>userId 由 JwtAuthFilter 放进 Authentication 的 principal。
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessException(401, "未登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            return userId;
        }
        throw new BusinessException(401, "登录状态异常");
    }
}
