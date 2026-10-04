package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户资料更新请求。
 *
 * <p>校验上限与前端 {@code profile.ts} 对齐：后端是最后一道闸，前端过了后端也得挡。
 * avatarUrl 存 objectKey（不是 URL，见 ADR-0002），可空 = 清空头像。
 */
@Schema(description = "用户资料更新请求")
public record UserProfileReq(

        @Schema(description = "昵称", example = "轻记用户")
        @NotBlank(message = "昵称不能为空")
        @Size(max = 12, message = "昵称最多 12 个字")
        String nickname,

        @Schema(description = "个性签名")
        @Size(max = 40, message = "签名最多 40 个字")
        String signature,

        @Schema(description = "邮箱", example = "name@example.com")
        @Size(max = 60, message = "邮箱最多 60 个字符")
        @Email(message = "邮箱格式不正确")
        String email,

        @Schema(description = "所在地(省·市·区纯文本)")
        @Size(max = 30, message = "所在地最多 30 个字")
        String location,

        @Schema(description = "头像objectKey")
        String avatarUrl) {
}
