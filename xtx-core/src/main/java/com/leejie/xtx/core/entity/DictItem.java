package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.BaseEntity;
import com.leejie.xtx.core.handler.JsonMapTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 字典项表（字典内容）。
 *
 * <p>autoResultMap = true 是 extra 自定义 TypeHandler 生效的前提（与 Record.images 同理）。
 */
@Schema(description = "字典项表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dict_item", autoResultMap = true)
public class DictItem extends BaseEntity {

    @Schema(description = "所属字典类型码")
    private String dictType;

    @Schema(description = "项键(机器值)")
    private String itemKey;

    @Schema(description = "展示名")
    private String itemLabel;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "启用(0-否,1-是)")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "类型专属属性，如 {\"color\":\"#7C3AED\"}")
    @TableField(typeHandler = JsonMapTypeHandler.class)
    private Map<String, Object> extra;
}
