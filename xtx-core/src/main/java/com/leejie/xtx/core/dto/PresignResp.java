package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * 图片预签名直传响应（S3 POST 表单）。
 *
 * <p>前端拿 postUrl + formData 调 {@code wx.uploadFile}：url 填 postUrl、file 字段放文件、
 * 其余字段原样塞进 formData，MinIO 校验通过后把对象落到 {@code objectKey}。
 * 再把 objectKey 放进 Record.images 提交。
 */
@Schema(description = "预签名直传响应")
public record PresignResp(

        @Schema(description = "POST 直传目标 URL（endpoint/bucket）")
        String postUrl,

        @Schema(description = "multipart 表单字段（key/policy/x-amz-*，前端原样转发）")
        Map<String, String> formData,

        @Schema(description = "对象键，提交记录时回传后端")
        String objectKey,

        @Schema(description = "postUrl 过期时间戳(ms)")
        Long expiresAt) {
}
