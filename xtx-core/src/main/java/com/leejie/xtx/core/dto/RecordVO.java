package com.leejie.xtx.core.dto;

import cn.hutool.core.bean.BeanUtil;
import com.leejie.xtx.core.entity.Record;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "记录表视图对象")
@Data
public class RecordVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "标题(空=无标题)")
    private String title;
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
    @Schema(description = "文字内容")
    private String content;
    /** fromEntity 出来时装的是 objectKey，由 Controller 换成 access URL 后才出站 */
    @Schema(description = "图片访问URL数组")
    private List<String> images;
    @Schema(description = "记录日期(支持补记)")
    private LocalDate recordDate;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    public static RecordVO fromEntity(Record entity) {
        return BeanUtil.copyProperties(entity, RecordVO.class);
    }
}