package com.leejie.xtx.common.jwt;

import com.leejie.xtx.common.constant.Constants;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

public class JwtUtils {

    // 显式 UTF-8：getBytes() 无参依赖平台默认字符集，Windows 中文环境可能为 GBK，
    // 同一密钥在不同环境会得到不同字节，导致 token 无法跨环境解析
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            Constants.TOKEN_SECRET.getBytes(StandardCharsets.UTF_8)
    );

    public static String generate(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + Constants.TOKEN_EXPIRATION))
                .signWith(SECRET_KEY)
                .compact();
    }

    public static Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean validate(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}