package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;

@Schema(description = "报告表更新请求")
@Data
public class ReportUpdateReq {

    @NotNull(message = "id不能为空")
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW")
    @NotNull(message = "模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW不能为空")
    private String template;
    @Schema(description = "报告标题")
    private String title;
    @Schema(description = "报告内容(Markdown)")
    private String content;
    @Schema(description = "覆盖开始日期")
    private LocalDate startDate;
    @Schema(description = "覆盖结束日期")
    private LocalDate endDate;
    @Schema(description = "筛选分类:LIFE/STUDY/ALL")
    private String category;
    @Schema(description = "基于多少条记录生成")
    private Integer recordCount;
    @Schema(description = "使用的模型名")
    private String model;
    @Schema(description = "消耗token数")
    private Integer tokensUsed;

    public Report toEntity() {
        Report entity = new Report();
        BeanUtils.copyProperties(this, entity);
        return entity;
    }
}