package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 笔记搜索结果项。
 *
 * <p>与 {@link RecordVO} 不同：不返回 content / images（结果卡片只展示标题+摘要，
 * 不签图片 URL 省一次 MinIO 往返）；excerpt 与 highlights 由服务端围绕首个命中生成。
 * id 是雪花 Long，Jackson 统一按字符串序列化（见 JacksonConfig）。
 */
@Schema(description = "笔记搜索结果项")
@Data
public class SearchVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "标题(空=无标题)")
    private String title;
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
    @Schema(description = "记录日期(支持补记)")
    private LocalDate recordDate;
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
    /** 围绕首个命中的纯文本摘要（可能带 … 截断），前端无高亮时的兜底文案 */
    @Schema(description = "摘要(围绕首个命中截取)")
    private String excerpt;
    /** 服务端生成的高亮 HTML（已转义，只含 &lt;span class="hl"&gt;） */
    @Schema(description = "高亮HTML(服务端转义,前端 rich-text 只渲染)")
    private SearchHighlights highlights;

    @Schema(description = "搜索高亮(HTML)")
    @Data
    public static class SearchHighlights {
        /** 标题高亮（未命中 = 转义后的纯标题） */
        @Schema(description = "标题高亮HTML")
        private String title;
        /** 摘要高亮HTML */
        @Schema(description = "摘要高亮HTML")
        private String excerpt;
    }
}
