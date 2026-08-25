package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 代理上传响应。
 */
@Schema(description = "上传响应")
public record UploadResp(

        @Schema(description = "对象键")
        String objectKey,

        @Schema(description = "原始文件名")
        String originalFilename,

        @Schema(description = "内容类型")
        String contentType,

        @Schema(description = "文件大小(字节)")
        Long size) {
}
