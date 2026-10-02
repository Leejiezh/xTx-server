package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信登录请求")
public record LoginReq(
        @Schema(description = "wx.login 获取的临时 code")
        @NotBlank(message = "code 不能为空")
        String code) {
}
