package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型表（类型目录：全项目有哪些字典）。
 *
 * <p>系统级共享数据，不继承 OwnedEntity —— 无 userId、无 deleted，删除语义由 enabled 承担。
 */
@Schema(description = "字典类型表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_type")
public class DictType extends BaseEntity {

    @Schema(description = "类型码，如 note_label")
    private String typeCode;

    @Schema(description = "类型名称，如 笔记标签")
    private String typeName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "整本字典启用(0-否,1-是)")
    private Integer enabled;
}
