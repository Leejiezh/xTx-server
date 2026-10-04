package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.DictItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "字典项视图对象")
@Data
public class DictItemVO {

    @Schema(description = "项键")
    private String key;

    @Schema(description = "展示名")
    private String label;

    @Schema(description = "排序")
    private Integer sortOrder;

    /** 类型专属属性原样透传，前端自行读取（如 extra.color），不在后端拍平 */
    @Schema(description = "类型专属属性")
    private Map<String, Object> extra;

    public static DictItemVO fromEntity(DictItem entity) {
        DictItemVO vo = new DictItemVO();
        vo.setKey(entity.getItemKey());
        vo.setLabel(entity.getItemLabel());
        vo.setSortOrder(entity.getSortOrder());
        vo.setExtra(entity.getExtra());
        return vo;
    }
}
