package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.Report;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;

@Schema(description = "报告表视图对象")
@Data
public class ReportVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW")
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

    public static ReportVO fromEntity(Report entity) {
        ReportVO vo = new ReportVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}