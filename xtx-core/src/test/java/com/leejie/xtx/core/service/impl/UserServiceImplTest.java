package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.dto.UserProfileReq;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.core.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserMapper userMapper;
    @Mock
    private FileService fileService;
    @Mock
    private CurrentUserProvider currentUser;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(userMapper, fileService, currentUser);
    }

    private User user(String avatar) {
        User u = new User();
        u.setId(USER_ID);
        u.setNickname("兔兔");
        u.setSignature("签名");
        u.setEmail("a@b.com");
        u.setLocation("杭州");
        u.setAvatarUrl(avatar);
        return u;
    }

    @Test
    @DisplayName("getProfile 返回资料，avatarUrl 现签 access URL")
    void getProfile_signsAvatarUrl() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/a.jpg"));
        when(fileService.accessUrl("img/1/a.jpg", false)).thenReturn("http://minio/xtx/img/1/a.jpg?x=1");

        UserVO vo = userService.getProfile();

        assertEquals("兔兔", vo.getNickname());
        assertEquals("http://minio/xtx/img/1/a.jpg?x=1", vo.getAvatarUrl());
        // 原始 objectKey 单独出站，前端保存资料时原样回传
        assertEquals("img/1/a.jpg", vo.getAvatarKey());
    }

    @Test
    @DisplayName("getProfile 头像文件异常时降级为空，不 404 整个资料")
    void getProfile_brokenAvatar_fallsBackToNull() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/a.jpg"));
        when(fileService.accessUrl("img/1/a.jpg", false)).thenThrow(new BusinessException(404, "文件不存在"));

        UserVO vo = userService.getProfile();

        assertNull(vo.getAvatarUrl());
        assertEquals("兔兔", vo.getNickname());
    }

    @Test
    @DisplayName("getProfile 无头像时不调 accessUrl")
    void getProfile_noAvatar_skipsSigning() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user(null));

        UserVO vo = userService.getProfile();

        assertNull(vo.getAvatarUrl());
        verify(fileService, never()).accessUrl(any(), anyBoolean());
    }

    @Test
    @DisplayName("当前用户不存在 → 404")
    void getCurrent_missingUser_throws404() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class, () -> userService.getProfile()).getCode());
    }

    @Test
    @DisplayName("updateProfile 更新字段；头像未变则不触碰文件服务")
    void updateProfile_sameAvatar_updatesFieldsOnly() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/a.jpg"));

        userService.updateProfile(new UserProfileReq("新昵称", "新签名", "x@y.com", "上海", "img/1/a.jpg"));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(cap.capture());
        assertEquals("新昵称", cap.getValue().getNickname());
        assertEquals("新签名", cap.getValue().getSignature());
        assertEquals("x@y.com", cap.getValue().getEmail());
        assertEquals("上海", cap.getValue().getLocation());
        assertEquals("img/1/a.jpg", cap.getValue().getAvatarUrl());
        verify(fileService, never()).requireOwned(any());
        verify(fileService, never()).detachAll(any());
    }

    @Test
    @DisplayName("换头像：校验新 key 归属，旧头像标 DETACHED")
    void updateProfile_changedAvatar_claimsNewAndDetachesOld() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/old.jpg"));

        userService.updateProfile(new UserProfileReq("兔兔", "", "", "", "img/1/new.jpg"));

        verify(fileService).requireOwned("img/1/new.jpg");
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(cap.capture());
        assertEquals("img/1/new.jpg", cap.getValue().getAvatarUrl());
        verify(fileService).detachAll(List.of("img/1/old.jpg"));
    }

    @Test
    @DisplayName("清空头像：avatarUrl 置 null，旧头像进宽限期，无归属校验")
    void updateProfile_clearAvatar_detachesOldOnly() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/old.jpg"));

        userService.updateProfile(new UserProfileReq("兔兔", "", "", "", ""));

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(cap.capture());
        assertNull(cap.getValue().getAvatarUrl());
        verify(fileService).detachAll(List.of("img/1/old.jpg"));
        verify(fileService, never()).requireOwned(any());
    }

    @Test
    @DisplayName("新头像不属于当前用户 → 404，不落库也不 detach 旧头像")
    void updateProfile_foreignAvatar_rejected() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(userMapper.selectById(USER_ID)).thenReturn(user("img/1/old.jpg"));
        doThrow(new BusinessException(404, "文件不存在")).when(fileService).requireOwned("img/1/foreign.jpg");

        assertEquals(404, assertThrows(BusinessException.class, () ->
                userService.updateProfile(new UserProfileReq("兔兔", "", "", "", "img/1/foreign.jpg"))).getCode());

        verify(userMapper, never()).updateById(any(User.class));
        verify(fileService, never()).detachAll(any());
    }
}
