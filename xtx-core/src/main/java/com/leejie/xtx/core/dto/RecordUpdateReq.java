package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.Record;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "记录表更新请求")
@Data
public class RecordUpdateReq {

    @NotNull(message = "id不能为空")
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "标题(空=无标题)")
    private String title;
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
    @Schema(description = "文字内容(与标题至少一项非空，service 层校验)")
    private String content;
    @Schema(description = "图片objectKey数组")
    private List<String> images;
    @Schema(description = "记录日期(支持补记)")
    @NotNull(message = "记录日期(支持补记)不能为空")
    private LocalDate recordDate;
    public Record toEntity() {
        Record entity = new Record();
        BeanUtils.copyProperties(this, entity);
        return entity;
    }
}