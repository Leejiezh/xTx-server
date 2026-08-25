package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 图片预签名上传请求。
 *
 * <p>size 由前端声明、无法在 MinIO 侧强制，因此只是预检；真实大小在 attach 时
 * 由 statObject 复核（见 {@code FileServiceImpl.requireUploaded}）。
 */
@Schema(description = "图片预签名上传请求")
public record PresignReq(

        @Schema(description = "内容类型", example = "image/jpeg")
        @NotBlank(message = "contentType 不能为空")
        String contentType,

        @Schema(description = "文件大小(字节)", example = "204800")
        @NotNull(message = "size 不能为空")
        @Positive(message = "size 必须大于 0")
        Long size,

        @Schema(description = "原始文件名", example = "photo.jpg")
        String originalFilename) {
}
