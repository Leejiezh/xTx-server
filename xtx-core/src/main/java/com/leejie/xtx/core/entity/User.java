package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户表不继承 OwnedEntity：user 表没有 user_id / deleted 列，归属校验（如有）须在 service 层手写 */
@Schema(description = "用户表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 微信openid */
    @Schema(description = "微信openid")
    private String openid;

    /** 昵称 */
    @Schema(description = "昵称")
    private String nickname;

    /** 头像URL */
    @Schema(description = "头像URL")
    private String avatarUrl;

    /** 个性签名 */
    @Schema(description = "个性签名")
    private String signature;

    /** 邮箱 */
    @Schema(description = "邮箱")
    private String email;

    /** 所在地(省·市·区纯文本，不存行政区划代码，见 .claude/rules/database.md) */
    @Schema(description = "所在地(省·市·区纯文本)")
    private String location;

    /** 每日AI生成配额 */
    @Schema(description = "每日AI生成配额")
    private Integer dailyQuota;

}
