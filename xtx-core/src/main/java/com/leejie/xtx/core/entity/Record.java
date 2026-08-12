package com.leejie.xtx.core.entity;

import com.leejie.xtx.common.base.entity.OwnedEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(description = "记录表")
@Data
@EqualsAndHashCode(callSuper = true)
public class Record extends OwnedEntity {

    /** 用户ID */
    @Schema(description = "用户ID")
    private Long userId;

    /** 分类:LIFE/STUDY */
    @Schema(description = "分类:LIFE/STUDY")
    private String category;

    /** 文字内容 */
    @Schema(description = "文字内容")
    private String content;

    /** 图片URL数组 */
    @Schema(description = "图片URL数组")
    private String images;

    /** 记录日期(支持补记) */
    @Schema(description = "记录日期(支持补记)")
    private LocalDate recordDate;

    /** 来源:MANUAL/IMAGE */
    @Schema(description = "来源:MANUAL/IMAGE")
    private String source;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

}