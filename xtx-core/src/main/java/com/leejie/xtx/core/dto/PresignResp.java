package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 预签名上传响应。前端拿 putUrl 直传 MinIO，再把 objectKey 放进 Record.images 提交。
 */
@Schema(description = "预签名上传响应")
public record PresignResp(

        @Schema(description = "预签名 PUT URL")
        String putUrl,

        @Schema(description = "对象键，提交记录时回传后端")
        String objectKey,

        @Schema(description = "putUrl 过期时间戳(ms)")
        Long expiresAt) {
}
