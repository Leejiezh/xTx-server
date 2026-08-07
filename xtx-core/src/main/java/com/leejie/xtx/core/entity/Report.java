package com.leejie.xtx.core.entity;

import com.leejie.xtx.common.base.entity.OwnedEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Schema(description = "报告表")
@Data
@EqualsAndHashCode(callSuper = true)
public class Report extends OwnedEntity {

    /** 用户ID */
    @Schema(description = "用户ID")
    private Long userId;

    /** 模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW */
    @Schema(description = "模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW")
    private String template;

    /** 报告标题 */
    @Schema(description = "报告标题")
    private String title;

    /** 报告内容(Markdown) */
    @Schema(description = "报告内容(Markdown)")
    private String content;

    /** 覆盖开始日期 */
    @Schema(description = "覆盖开始日期")
    private LocalDate startDate;

    /** 覆盖结束日期 */
    @Schema(description = "覆盖结束日期")
    private LocalDate endDate;

    /** 筛选分类:LIFE/STUDY/ALL */
    @Schema(description = "筛选分类:LIFE/STUDY/ALL")
    private String category;

    /** 基于多少条记录生成 */
    @Schema(description = "基于多少条记录生成")
    private Integer recordCount;

    /** 使用的模型名 */
    @Schema(description = "使用的模型名")
    private String model;

    /** 消耗token数 */
    @Schema(description = "消耗token数")
    private Integer tokensUsed;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

}