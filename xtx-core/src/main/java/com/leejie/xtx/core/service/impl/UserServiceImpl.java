package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.dto.UserProfileReq;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.core.service.FileService;
import com.leejie.xtx.core.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final FileService fileService;
    private final CurrentUserProvider currentUser;

    @Override
    public UserVO getProfile() {
        return toVO(getCurrent());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(UserProfileReq req) {
        User user = getCurrent();
        String newAvatar = normalizeKey(req.avatarUrl());
        String oldAvatar = normalizeKey(user.getAvatarUrl());

        boolean avatarChanged = !Objects.equals(newAvatar, oldAvatar);
        // 头像变更：新 key 必须是当前用户已上传的文件（presign 落过 TEMP），否则 404
        if (avatarChanged && newAvatar != null) {
            fileService.requireOwned(newAvatar);
        }

        user.setNickname(req.nickname());
        user.setSignature(req.signature());
        user.setEmail(req.email());
        user.setLocation(req.location());
        user.setAvatarUrl(avatarChanged ? newAvatar : oldAvatar);
        userMapper.updateById(user);

        // 旧头像不再被 avatar_url 引用，标 DETACHED 后由 sweeper 在宽限期后清理
        if (avatarChanged && oldAvatar != null) {
            fileService.detachAll(List.of(oldAvatar));
        }
    }

    /** 空白视为 null（前端可能传空串表示清空），避免误判成「换了个空头像」 */
    private static String normalizeKey(String key) {
        return key == null || key.isBlank() ? null : key;
    }

    private User getCurrent() {
        Long userId = currentUser.currentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private UserVO toVO(User user) {
        UserVO vo = UserVO.fromEntity(user);
        // 原始 key 先存 avatarKey，avatarUrl 再被换成会过期的 access URL（前端回传 key 而非 URL）
        vo.setAvatarKey(user.getAvatarUrl());
        if (vo.getAvatarUrl() != null && !vo.getAvatarUrl().isBlank()) {
            try {
                vo.setAvatarUrl(fileService.accessUrl(vo.getAvatarUrl(), false));
            } catch (BusinessException e) {
                // 头像文件被清理或不属于当前用户：展示不该因一条脏头像 404 整个资料，降级为空
                vo.setAvatarUrl(null);
            }
        }
        return vo;
    }
}
