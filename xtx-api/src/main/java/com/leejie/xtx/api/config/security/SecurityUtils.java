package com.leejie.xtx.api.config.security;

import com.leejie.xtx.common.exception.BusinessException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 当前登录用户工具类。
 *
 * <p>userId 由 {@code JwtAuthFilter} 放进 Authentication 的 principal（Long），
 * 这里是 SecurityContext 的唯一读取出口，401 判定与「登录状态异常」兜底都集中在本类。
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
