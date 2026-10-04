package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import com.leejie.xtx.core.mapper.UserMapper;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplSweepTest {

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private FileMetadataMapper fileMetadataMapper;
    @Mock
    private CurrentUserProvider currentUser;
    @Mock
    private UserMapper userMapper;

    private final FileProperties fileProperties = new FileProperties();

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(minioClient, minioConfig, fileMetadataMapper, fileProperties, currentUser, userMapper);
    }

    private FileMetadata orphan(String key, String status) {
        FileMetadata meta = new FileMetadata();
        meta.setObjectKey(key);
        meta.setUserId(1L);
        meta.setStatus(status);
        return meta;
    }

    @Test
    @DisplayName("清理任务删除超期的 TEMP / DETACHED，对象与元数据行一并物理删除")
    void sweepOrphans_removesObjectsAndRows() throws Exception {
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectList(any()))
                .thenReturn(List.of(orphan("img/1/a.jpg", "TEMP"), orphan("file/1/b.pdf", "DETACHED")));
        when(userMapper.selectList(any())).thenReturn(List.of());

        assertEquals(2, fileService.sweepOrphans());

        verify(minioClient, org.mockito.Mockito.times(2)).removeObject(any(RemoveObjectArgs.class));
        verify(fileMetadataMapper).deleteById("img/1/a.jpg");
        verify(fileMetadataMapper).deleteById("file/1/b.pdf");
    }

    @Test
    @DisplayName("没有孤儿文件时返回 0，不触碰 MinIO")
    void sweepOrphans_noOrphans_returnsZero() throws Exception {
        when(fileMetadataMapper.selectList(any())).thenReturn(List.of());

        assertEquals(0, fileService.sweepOrphans());

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("对象删除失败时保留元数据行，下一轮重扫时重试，且不影响同批其他文件")
    void sweepOrphans_objectRemovalFails_keepsRowAndContinues() throws Exception {
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectList(any()))
                .thenReturn(List.of(orphan("bad", "TEMP"), orphan("good", "TEMP")));
        when(userMapper.selectList(any())).thenReturn(List.of());
        doThrow(new RuntimeException("minio down"))
                .when(minioClient).removeObject(argsWithObject("bad"));

        assertEquals(1, fileService.sweepOrphans());

        verify(fileMetadataMapper, never()).deleteById("bad");
        verify(fileMetadataMapper).deleteById("good");
    }

    /** RemoveObjectArgs 没有 equals，只能按 object() 自定义匹配 */
    private RemoveObjectArgs argsWithObject(String objectKey) {
        return org.mockito.ArgumentMatchers.argThat(args -> args != null && objectKey.equals(args.object()));
    }

    /** 清理任务不依赖登录态：整套流程跑完也不应该问 CurrentUserProvider 要 userId */
    @Test
    @DisplayName("清理任务不读取登录态")
    void sweepOrphans_doesNotTouchLoginContext() {
        when(fileMetadataMapper.selectList(any())).thenReturn(List.of());

        fileService.sweepOrphans();

        verify(currentUser, never()).currentUserId();
    }

    @Test
    @DisplayName("被 user.avatar_url 引用的 TEMP 文件视为有效头像，不被清理")
    void sweepOrphans_skipsAvatarReferencedKeys() throws Exception {
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectList(any()))
                .thenReturn(List.of(orphan("img/1/avatar.jpg", "TEMP"), orphan("img/1/orphan.jpg", "TEMP")));
        User u = new User();
        u.setAvatarUrl("img/1/avatar.jpg");
        when(userMapper.selectList(any())).thenReturn(List.of(u));

        assertEquals(1, fileService.sweepOrphans());

        verify(minioClient).removeObject(argsWithObject("img/1/orphan.jpg"));
        verify(fileMetadataMapper, never()).deleteById("img/1/avatar.jpg");
        verify(fileMetadataMapper).deleteById("img/1/orphan.jpg");
    }
}
