package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据：跟踪每个已上传文件的生命周期（见 ADR-0003）。
 *
 * <p>不继承 {@code OwnedEntity}：主键是 objectKey 而非自增 id，且无 deleted 列
 * —— 清理任务要物理删行，逻辑删除会让已删对象的行永久滞留。归属校验因此在
 * {@code FileServiceImpl} 里手写，不复用 {@code OwnedServiceImpl}。
 */
@Schema(description = "文件元数据")
@Data
@TableName("file_metadata")
public class FileMetadata {

    /** objectKey 由后端生成，前端无从伪造，故用 INPUT 而非自增 */
    @Schema(description = "对象键(主键)")
    @TableId(type = IdType.INPUT)
    private String objectKey;

    @Schema(description = "用户ID")
    private Long userId;

    /** 下载时用于 Content-Disposition，objectKey 里只有 uuid 拿不到它 */
    @Schema(description = "原始文件名")
    private String originalFilename;

    @Schema(description = "内容类型")
    private String contentType;

    @Schema(description = "文件大小(字节)")
    private Long size;

    @Schema(description = "状态:TEMP/ATTACHED/DETACHED")
    private String status;

    @Schema(description = "关联记录ID(ATTACHED时非空)")
    private Long recordId;

    /** 由 MySQL DEFAULT CURRENT_TIMESTAMP 生成，insert 后本字段仍为 null */
    @Schema(description = "上传时间")
    private LocalDateTime createdAt;

    @Schema(description = "附加时间")
    private LocalDateTime attachedAt;
}
