package com.leejie.xtx.common.base.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 「属于某个用户」的实体基类 —— record / report 用，user 表不用(它自己就是用户)。
 *
 * <p>userId 与 deleted 放在一起，是因为这两列总是同时出现：凡是用户私有数据都要
 * 逻辑删除。分开声明的话，每个实体都得记得给 deleted 加 {@code @TableLogic}，
 * 漏一个就变成硬删。DDL 里 record / report 两张表的这两列完全一致。
 *
 * <p>这两个字段由 {@code OwnedServiceImpl} 维护，业务代码不要手动赋值：
 * userId 在 create 时被强制覆盖，deleted 由 MyBatis-Plus 的逻辑删除接管。
 *
 * <p>子类用 Lombok 时记得加 {@code @EqualsAndHashCode(callSuper = true)}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class OwnedEntity extends BaseEntity {

    /** 归属用户 ID */
    private Long userId;

    /** 逻辑删除标记(0-正常, 1-删除)，MyBatis-Plus 自动在查询上追加 deleted = 0 */
    @TableLogic
    private Integer deleted;
}
