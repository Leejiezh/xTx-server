package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.Record;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;

@Schema(description = "记录表视图对象")
@Data
public class RecordVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "分类:LIFE/STUDY")
    private String category;
    @Schema(description = "文字内容")
    private String content;
    @Schema(description = "图片URL数组")
    private String images;
    @Schema(description = "记录日期(支持补记)")
    private LocalDate recordDate;
    @Schema(description = "来源:MANUAL/IMAGE")
    private String source;

    public static RecordVO fromEntity(Record entity) {
        RecordVO vo = new RecordVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}