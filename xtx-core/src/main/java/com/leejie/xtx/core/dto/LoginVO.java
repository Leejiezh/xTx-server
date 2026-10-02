package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record LoginVO(
        @Schema(description = "JWT，前端存本地并在后续请求的 Authorization 头回传")
        String token,
        @Schema(description = "用户信息")
        UserVO userInfo) {
}
