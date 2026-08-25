package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.OwnedEntity;
import com.leejie.xtx.core.handler.JsonListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/** autoResultMap 是 images 自定义 TypeHandler 生效的前提：没有它，查询结果不会走 JsonListTypeHandler */
@Schema(description = "记录表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "record", autoResultMap = true)
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

    /** 图片objectKey数组，读时才签发 access URL（见 ADR-0002） */
    @Schema(description = "图片objectKey数组")
    @TableField(typeHandler = JsonListTypeHandler.class)
    private List<String> images;

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