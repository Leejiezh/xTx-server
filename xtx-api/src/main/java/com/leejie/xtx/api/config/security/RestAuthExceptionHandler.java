package com.leejie.xtx.api.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leejie.xtx.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 把 Spring Security 的认证/授权失败写成统一的 {@link R} JSON。
 *
 * <p>为什么必须有这个类：认证异常是在 filter chain 里抛出的，请求根本没进
 * DispatcherServlet，{@code GlobalExceptionHandler}（@RestControllerAdvice）接不到。
 * 不挂它，Security 默认的 entry point 只设 401 状态码和 WWW-Authenticate 头，
 * **不写任何响应体** —— 客户端拿到一个没有任何信息的 401。
 *
 * <p>entry point 与 access denied handler 合成一个类：两者只是状态码和文案不同，
 * 拆两个文件会把同一段 JSON 写出逻辑复制两遍。
 */
@Component
@RequiredArgsConstructor
public class RestAuthExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED.value(), R.unauthorized("未登录或登录已过期"));
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN.value(), R.forbidden("无权访问"));
    }

    private void write(HttpServletResponse response, int status, R<Void> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
