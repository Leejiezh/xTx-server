package com.leejie.xtx.api.config.security;

import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 解析 Bearer token，把 userId 填进 SecurityContext，供 {@link SecurityUtils} 读取。
 *
 * <p>故意不加 {@code @Component}：Spring Boot 会把任意 Filter bean 额外注册成容器级
 * filter，与 SecurityConfig 的 addFilterBefore 形成双注册，而 OncePerRequestFilter
 * 的 already-filtered 标记会让其中一次静默跳过 —— 究竟哪次生效取决于 filter order，
 * 太脆弱。本类无依赖（JwtUtils 全静态），由 SecurityConfig 直接 new。
 *
 * <p>token 无效时只清空上下文并放行，不在这里写响应：让「没带 token」和「token 坏了」
 * 共用 {@link RestAuthExceptionHandler} 这一个出口，避免两处各维护一份 401 响应体。
 */
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(Constants.TOKEN_HEADER);

        if (authHeader != null && authHeader.startsWith(Constants.TOKEN_PREFIX)) {
            try {
                Claims claims = JwtUtils.parse(authHeader.substring(Constants.TOKEN_PREFIX.length()));

                // 不能写 claims.get("userId", Long.class)：JSON 数字反序列化后小值是 Integer，
                // jjwt 会抛类型不匹配。且 principal 必须是 Long —— SecurityUtils 用 instanceof Long 取值。
                Long userId = ((Number) claims.get("userId")).longValue();

                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            } catch (Exception e) {
                log.warn("JWT 校验失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
