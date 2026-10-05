package com.leejie.xtx.core.dto;

import com.leejie.xtx.common.base.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 记录分页查询参数
 */
@Schema(description = "记录分页查询参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class RecordQuery extends PageQuery {

    @Schema(description = "标签:dict_item.item_key(空=不过滤)")
    @Size(max = 64, message = "标签长度不能超过64")
    private String label;
}
