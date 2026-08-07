package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.Record;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;

@Schema(description = "记录表创建请求")
@Data
public class RecordCreateReq {

    @Schema(description = "分类:LIFE/STUDY")
    @NotBlank(message = "分类:LIFE/STUDY不能为空")
    private String category;
    @Schema(description = "文字内容")
    @NotBlank(message = "文字内容不能为空")
    private String content;
    @Schema(description = "图片URL数组")
    private String images;
    @Schema(description = "记录日期(支持补记)")
    @NotNull(message = "记录日期(支持补记)不能为空")
    private LocalDate recordDate;
    @Schema(description = "来源:MANUAL/IMAGE")
    @NotBlank(message = "来源:MANUAL/IMAGE不能为空")
    private String source;

    public Record toEntity() {
        Record entity = new Record();
        BeanUtils.copyProperties(this, entity);
        return entity;
    }
}