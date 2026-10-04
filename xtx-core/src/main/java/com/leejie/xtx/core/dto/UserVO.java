package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Schema(description = "用户视图对象(精简)")
@Data
public class UserVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "昵称")
    private String nickname;
    /** fromEntity 出来装的是 objectKey，由 UserController 换成 access URL 后才出站 */
    @Schema(description = "头像访问URL")
    private String avatarUrl;
    /** 头像 objectKey：前端保存资料时原样回传（未换头像就不动它），换头像时传新 key */
    @Schema(description = "头像objectKey")
    private String avatarKey;
    @Schema(description = "个性签名")
    private String signature;
    @Schema(description = "邮箱")
    private String email;
    @Schema(description = "所在地(省·市·区纯文本)")
    private String location;

    public static UserVO fromEntity(User entity) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
