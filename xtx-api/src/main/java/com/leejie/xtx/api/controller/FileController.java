package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.PresignReq;
import com.leejie.xtx.core.dto.PresignResp;
import com.leejie.xtx.core.dto.UploadResp;
import com.leejie.xtx.core.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传/访问端点。
 *
 * <p>objectKey 一律走 {@code @RequestParam} 而非路径变量：它形如
 * {@code img/1/2026/08/24/uuid.jpg}，带斜杠，放进 path variable 会被路由截断。
 */
@Tag(name = "文件管理")
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/presign")
    @Operation(summary = "获取图片预签名上传URL")
    public R<PresignResp> presign(@Valid @RequestBody PresignReq req) {
        return R.ok(fileService.presign(req));
    }

    @PostMapping("/upload")
    @Operation(summary = "上传文件(非图片走后端代理)")
    public R<UploadResp> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(fileService.upload(file));
    }

    @GetMapping("/url")
    @Operation(summary = "获取文件访问URL(预览/下载)")
    public R<String> url(@RequestParam String objectKey,
                         @RequestParam(defaultValue = "false") boolean download) {
        return R.ok(fileService.accessUrl(objectKey, download));
    }

    @DeleteMapping
    @Operation(summary = "删除文件")
    public R<Void> delete(@RequestParam String objectKey) {
        fileService.delete(objectKey);
        return R.ok();
    }
}
