package com.leejie.xtx.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件上传与访问的配置。
 *
 * <p>字段默认值即生产可用值，application.yml 里的 file.* 只是把它们显式化，
 * 便于运维改动 —— 不配置也能起。
 */
@Data
@Component
@ConfigurationProperties(prefix = "file")
public class FileProperties {

    /** 允许的图片 content-type，走预签名直传 */
    private List<String> imageTypes = List.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");

    /** 允许的文档 content-type，走后端代理上传 */
    private List<String> documentTypes = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "application/zip");

    /** 图片大小上限(字节) */
    private long imageMaxSize = 10 * 1024 * 1024L;

    /** 文档大小上限(字节) */
    private long documentMaxSize = 50 * 1024 * 1024L;

    /** 预签名 PUT URL 有效期(秒)，只需覆盖前端一次上传 */
    private int presignedPutExpiry = 900;

    /**
     * 预签名 GET URL 有效期(秒)。
     * MinIO 上限为 7 天，不能再调大 —— 长期有效靠「读时现签发」而非长有效期(见 ADR-0002)。
     */
    private int presignedGetExpiry = 7 * 24 * 60 * 60;

    /** 孤儿文件宽限期(小时)：TEMP/DETACHED 超过该时长才被清理 */
    private int orphanGraceHours = 24;
}
