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
    @Schema(description = "头像URL")
    private String avatarUrl;

    public static UserVO fromEntity(User entity) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
