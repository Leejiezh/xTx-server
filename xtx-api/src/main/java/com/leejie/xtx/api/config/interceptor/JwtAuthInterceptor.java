package com.leejie.xtx.api.config.interceptor;

import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authHeader = request.getHeader(Constants.TOKEN_HEADER);
        if (authHeader == null || !authHeader.startsWith(Constants.TOKEN_PREFIX)) {
            response.setStatus(401);
            return false;
        }

        String token = authHeader.substring(Constants.TOKEN_PREFIX.length());
        Claims claims = JwtUtils.parse(token);
        request.setAttribute("userId", claims.get("userId", String.class));
        request.setAttribute("openid", claims.get("openid", String.class));
        return true;
    }
}