package com.leejie.xtx.common.constant;

public interface Constants {

    String TOKEN_HEADER = "Authorization";
    String TOKEN_PREFIX = "Bearer ";
    String TOKEN_SECRET = "xtx-server-secret-key-change-in-production";
    long TOKEN_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7天
    String CLAIM_USER_ID = "userId";
    String CLAIM_OPENID = "openid";

    String REDIS_PREFIX = "xtx:";
    String REDIS_TOKEN_KEY = REDIS_PREFIX + "token:";
    String REDIS_WX_SESSION_KEY = REDIS_PREFIX + "wx:session:";
}