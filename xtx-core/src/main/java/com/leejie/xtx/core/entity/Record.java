package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.OwnedEntity;
import com.leejie.xtx.core.handler.JsonListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

/** autoResultMap 是 images 自定义 TypeHandler 生效的前提：没有它，查询结果不会走 JsonListTypeHandler */
@Schema(description = "记录表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "record", autoResultMap = true)
public class Record extends OwnedEntity {

    /** 用户ID */
    @Schema(description = "用户ID")
    private Long userId;

    /**
     * 标题（可空：只有正文的笔记没有标题）。
     *
     * <p>用默认 NOT_NULL 更新策略：编辑器总是提交字符串（空串即"无标题"），
     * 不依赖 null 来清空，因此不需要像 label 那样用 ALWAYS。
     */
    @Schema(description = "标题(空=无标题)")
    private String title;

    /**
     * 标签:dict_item.item_key(空=未分类)。
     *
     * <p>ALWAYS 策略是必需的：NULL 表示"未分类"，而默认 NOT_NULL 更新策略会跳过 null，
     * 导致改回未分类时旧标签删不掉。images 不能用这个策略（null=未提交，见 RecordServiceImpl）。
     */
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String label;

    /** 文字内容（可空串：标题即笔记时正文为空） */
    @Schema(description = "文字内容")
    private String content;

    /** 图片objectKey数组，读时才签发 access URL（见 ADR-0002） */
    @Schema(description = "图片objectKey数组")
    @TableField(typeHandler = JsonListTypeHandler.class)
    private List<String> images;

    /** 记录日期(支持补记) */
    @Schema(description = "记录日期(支持补记)")
    private LocalDate recordDate;

    /** 创建时间 */
    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * 进回收站时间(NULL=正常)。删除笔记时置值，普通查询一律排除；
     * 彻底删除时随 @TableLogic 的 deleted=1 一起隐藏，此后不可恢复。
     */
    @Schema(description = "进回收站时间(NULL=正常)")
    private LocalDateTime recycledAt;

}
