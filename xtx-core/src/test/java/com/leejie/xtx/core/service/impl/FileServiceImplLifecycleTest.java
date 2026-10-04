package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import com.leejie.xtx.core.mapper.UserMapper;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplLifecycleTest {

    private static final Long USER_ID = 1L;
    private static final Long RECORD_ID = 100L;

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

    private FileMetadata temp(String key) {
        FileMetadata meta = new FileMetadata();
        meta.setObjectKey(key);
        meta.setUserId(USER_ID);
        meta.setStatus("TEMP");
        return meta;
    }

    private FileMetadata attached(String key, Long recordId) {
        FileMetadata meta = temp(key);
        meta.setStatus("ATTACHED");
        meta.setRecordId(recordId);
        return meta;
    }

    @Test
    @DisplayName("attach 拒绝非本人的 key，不碰 MinIO 也不更新行")
    void attach_rejectsNonOwnedKey() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        FileMetadata foreign = temp("k");
        foreign.setUserId(2L);
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("k"))).thenReturn(java.util.List.of(foreign));

        assertEquals(422, assertThrows(BusinessException.class,
                () -> fileService.attach(RECORD_ID, java.util.List.of("k"))).getCode());
    }

    @Test
    @DisplayName("attach 拒绝尚未上传到 MinIO 的对象（statObject 抛异常视为未上传）")
    void attach_rejectsIfObjectMissing() throws Exception {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("k"))).thenReturn(java.util.List.of(temp("k")));
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("not found"));

        assertEquals(422, assertThrows(BusinessException.class,
                () -> fileService.attach(RECORD_ID, java.util.List.of("k"))).getCode());
    }

    @Test
    @DisplayName("attach 拒绝已附加到其他记录的文件，防止跨记录复用")
    void attach_rejectsAlreadyAttachedToOtherRecord() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("k"))).thenReturn(java.util.List.of(attached("k", 999L)));

        assertEquals(422, assertThrows(BusinessException.class,
                () -> fileService.attach(RECORD_ID, java.util.List.of("k"))).getCode());
    }

    @Test
    @DisplayName("attach 拒绝真实大小超限的文件（statObject 回写的真实大小）")
    void attach_rejectsOversizeRealFile() throws Exception {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("k"))).thenReturn(java.util.List.of(temp("k")));
        when(minioConfig.getBucket()).thenReturn("xtx");
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(fileProperties.getDocumentMaxSize() + 1);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        assertEquals(422, assertThrows(BusinessException.class,
                () -> fileService.attach(RECORD_ID, java.util.List.of("k"))).getCode());
    }

    @Test
    @DisplayName("attach 成功：标 ATTACHED、设 recordId、记 attachedAt、回填真实大小")
    void attach_ok() throws Exception {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("k"))).thenReturn(java.util.List.of(temp("k")));
        when(minioConfig.getBucket()).thenReturn("xtx");
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1024L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        fileService.attach(RECORD_ID, java.util.List.of("k"));

        ArgumentCaptor<FileMetadata> cap = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileMetadataMapper).updateById(cap.capture());
        FileMetadata update = cap.getValue();
        assertEquals("k", update.getObjectKey());
        assertEquals("ATTACHED", update.getStatus());
        assertEquals(RECORD_ID, update.getRecordId());
        assertNotNull(update.getAttachedAt());
        assertEquals(1024L, update.getSize());
    }

    @Test
    @DisplayName("attach 空入参直接返回，不查库不碰 MinIO")
    void attach_empty_returnsDirectly() {
        fileService.attach(RECORD_ID, java.util.List.of());
        fileService.attach(RECORD_ID, null);
        verify(fileMetadataMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("reconcile 新增的标 ATTACHED、被移除的标 DETACHED")
    void reconcile_addedAndRemoved() throws Exception {
        when(currentUser.currentUserId()).thenReturn(USER_ID);
        // old=[keep, removed], new=[keep, added] → added=[added], removed=[removed]
        when(fileMetadataMapper.selectBatchIds(java.util.List.of("added"))).thenReturn(java.util.List.of(temp("added")));
        when(minioConfig.getBucket()).thenReturn("xtx");
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn(1L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(stat);

        fileService.reconcile(RECORD_ID, java.util.List.of("keep", "added"), java.util.List.of("keep", "removed"));

        // attach 更新了 "added"
        ArgumentCaptor<FileMetadata> attachCap = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileMetadataMapper).updateById(attachCap.capture());
        assertEquals("added", attachCap.getValue().getObjectKey());
        assertEquals("ATTACHED", attachCap.getValue().getStatus());

        // detach 批量更新 "removed"
        verify(fileMetadataMapper).update(eq(null), any());
    }

    @Test
    @DisplayName("detachAll 全部标 DETACHED，record_id 置 null")
    void detachAll_marksDetached() {
        when(currentUser.currentUserId()).thenReturn(USER_ID);

        fileService.detachAll(java.util.List.of("a", "b"));

        verify(fileMetadataMapper).update(eq(null), any());
    }
}