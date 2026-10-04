package com.leejie.xtx.core.service;

import com.leejie.xtx.core.dto.UserProfileReq;
import com.leejie.xtx.core.dto.UserVO;

/**
 * 用户资料服务。
 *
 * <p>user 表不继承 OwnedEntity（无 user_id / deleted），不适用 OwnedService；
 * 但「当前登录用户」本身就是归属边界 —— 这里所有操作都以
 * {@code CurrentUserProvider.currentUserId()} 定位本用户，调用者没有
 * 「改别人资料」的表达能力。
 */
public interface UserService {

    /**
     * 当前用户资料。
     *
     * <p>avatarUrl 出站前已签 access URL；头像文件缺失/非本人时降级为 null，
     * 不因一条脏头像让整个资料 404。
     */
    UserVO getProfile();

    /**
     * 更新当前用户资料。
     *
     * <p>换头像时：新 key 校验归属（{@code FileService.requireOwned}）；
     * 旧头像标 DETACHED 进入清理宽限期（不再被 avatar_url 引用，sweeper 会清）。
     */
    void updateProfile(UserProfileReq req);
}
