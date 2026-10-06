package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 标签计数（按当前用户统计）。
 *
 * <p>以 note_label 字典为基准（LEFT JOIN 笔记计数），因此返回**全部启用标签**
 * （含 0 笔记），并按 sortOrder 排序；{@code key} 即 {@code dict_item.item_key}
 * （= {@code record.label}，也是首页筛选值），颜色等类型专属属性原样透传在 {@code extra}。
 */
@Schema(description = "标签计数视图对象")
@Data
public class LabelCountVO {

    @Schema(description = "标签键:dict_item.item_key(也是首页筛选值)")
    private String key;

    @Schema(description = "标签展示名:dict_item.item_label")
    private String label;

    @Schema(description = "类型专属属性(如 color.light/dark)")
    private Map<String, Object> extra;

    @Schema(description = "该标签下的笔记数(0=无笔记)")
    private Long count;
}
