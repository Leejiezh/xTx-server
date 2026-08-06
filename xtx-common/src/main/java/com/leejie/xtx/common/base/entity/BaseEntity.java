package com.leejie.xtx.common.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类 —— 所有表共有的字段。
 *
 * <p>createdAt / updatedAt 故意不加 {@code @TableField(fill = ...)}：
 * 建表 DDL 已经声明了 {@code DEFAULT CURRENT_TIMESTAMP} 与
 * {@code ON UPDATE CURRENT_TIMESTAMP}，时间戳由 MySQL 生成，项目里没有
 * MetaObjectHandler。带来的后果是 {@code save()} 之后实体对象里
 * createdAt 仍为 null —— 所以新增接口只返回主键 id，不回填完整 VO。
 *
 * <p>只放 user / record / report 三张表都有的列。userId 和 deleted
 * 只有 record / report 有（user 表没这两列），所以由这两个实体各自声明，
 * 不往基类塞。
 *
 * <p>子类用 Lombok 时记得加 {@code @EqualsAndHashCode(callSuper = true)}。
 */
@Data
public abstract class BaseEntity implements Serializable {

    /** 主键，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建时间，由 MySQL 默认值生成 */
    private LocalDateTime createdAt;

    /** 更新时间，由 MySQL ON UPDATE 维护 */
    private LocalDateTime updatedAt;
}
