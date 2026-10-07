package com.leejie.xtx.core.dto;

import com.leejie.xtx.common.base.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 笔记搜索查询参数
 */
@Schema(description = "笔记搜索查询参数")
@Data
@EqualsAndHashCode(callSuper = true)
public class SearchQuery extends PageQuery {

    @Schema(description = "关键词(空=不过滤;标题与正文子串匹配,不区分大小写)")
    @Size(max = 100, message = "关键词长度不能超过100")
    private String q;

    @Schema(description = "标签:dict_item.item_key(空=不过滤)")
    @Size(max = 64, message = "标签长度不能超过64")
    private String label;
}
