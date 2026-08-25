package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplAccessTest {

    private static final Long OWNER = 1L;
    private static final Long INTRUDER = 2L;

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private FileMetadataMapper fileMetadataMapper;
    @Mock
    private CurrentUserProvider currentUser;

    private final FileProperties fileProperties = new FileProperties();

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileServiceImpl(minioClient, minioConfig, fileMetadataMapper, fileProperties, currentUser);
    }

    /** 属于 OWNER 的元数据 */
    private FileMetadata owned(String key) {
        FileMetadata meta = new FileMetadata();
        meta.setObjectKey(key);
        meta.setUserId(OWNER);
        meta.setOriginalFilename("我的报告.pdf");
        meta.setContentType("application/pdf");
        meta.setStatus("ATTACHED");
        return meta;
    }

    private String dispositionOf(GetPresignedObjectUrlArgs args) {
        return String.join("", args.extraQueryParams().get("response-content-disposition"));
    }

    @Test
    @DisplayName("accessUrl 对非本人的文件返回 404 而非 403，不给越权探测的机会")
    void accessUrl_notOwned_throws404() {
        when(currentUser.currentUserId()).thenReturn(INTRUDER);
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));

        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.accessUrl("k", false));

        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("accessUrl 对不存在的文件返回 404")
    void accessUrl_missing_throws404() {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(fileMetadataMapper.selectById("k")).thenReturn(null);

        assertEquals(404, assertThrows(BusinessException.class, () -> fileService.accessUrl("k", false)).getCode());
    }

    @Test
    @DisplayName("预览签发 inline 的 GET URL")
    void accessUrl_ok_inline() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://inline");

        assertEquals("http://inline", fileService.accessUrl("k", false));

        ArgumentCaptor<GetPresignedObjectUrlArgs> cap = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(cap.capture());
        assertEquals(Method.GET, cap.getValue().method());
        assertEquals("inline", dispositionOf(cap.getValue()));
    }

    @Test
    @DisplayName("下载签发 attachment 的 GET URL，文件名取自元数据而非 objectKey")
    void accessUrl_ok_download_carriesOriginalFilename() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://dl");

        assertEquals("http://dl", fileService.accessUrl("k", true));

        ArgumentCaptor<GetPresignedObjectUrlArgs> cap = ArgumentCaptor.forClass(GetPresignedObjectUrlArgs.class);
        verify(minioClient).getPresignedObjectUrl(cap.capture());
        assertTrue(dispositionOf(cap.getValue()).startsWith("attachment; filename=\"我的报告.pdf\""),
                dispositionOf(cap.getValue()));
    }

    @Test
    @DisplayName("accessUrls 保持入参顺序，不受 selectBatchIds 返回顺序影响")
    void accessUrls_keepsInputOrder() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        // 故意乱序返回
        when(fileMetadataMapper.selectBatchIds(List.of("ka", "kb")))
                .thenReturn(List.of(owned("kb"), owned("ka")));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://ka", "http://kb");

        assertEquals(List.of("http://ka", "http://kb"), fileService.accessUrls(List.of("ka", "kb")));
    }

    @Test
    @DisplayName("accessUrls 跳过脏 key 而不整页 404 —— 但绝不为非本人的 key 签发 URL")
    void accessUrls_skipsMissingAndForeignKeys() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        FileMetadata foreign = owned("kf");
        foreign.setUserId(INTRUDER);
        when(fileMetadataMapper.selectBatchIds(List.of("ka", "kf", "kmissing")))
                .thenReturn(List.of(owned("ka"), foreign));
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("http://ka");

        assertEquals(List.of("http://ka"), fileService.accessUrls(List.of("ka", "kf", "kmissing")));
        verify(minioClient).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }

    @Test
    @DisplayName("accessUrls 空入参直接返回空表，不查库")
    void accessUrls_empty_returnsEmpty() {
        assertTrue(fileService.accessUrls(List.of()).isEmpty());
        assertTrue(fileService.accessUrls(null).isEmpty());
        verify(fileMetadataMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("delete 对非本人的文件 404，且不动 MinIO 对象")
    void delete_notOwned_throws404() throws Exception {
        when(currentUser.currentUserId()).thenReturn(INTRUDER);
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));

        assertEquals(404, assertThrows(BusinessException.class, () -> fileService.delete("k")).getCode());

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
        verify(fileMetadataMapper, never()).deleteById((Serializable) any());
    }

    @Test
    @DisplayName("delete 成功：MinIO 对象与元数据行一并物理删除")
    void delete_ok_removesObjectAndRow() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));

        fileService.delete("k");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
        verify(fileMetadataMapper).deleteById("k");
    }

    @Test
    @DisplayName("MinIO 删除失败时保留元数据行，让用户可以重试")
    void delete_minioFails_keepsRow() throws Exception {
        when(currentUser.currentUserId()).thenReturn(OWNER);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(fileMetadataMapper.selectById("k")).thenReturn(owned("k"));
        doThrowOnRemove();

        assertThrows(BusinessException.class, () -> fileService.delete("k"));

        verify(fileMetadataMapper, never()).deleteById((Serializable) any());
    }

    private void doThrowOnRemove() throws Exception {
        org.mockito.Mockito.doThrow(new RuntimeException("minio down"))
                .when(minioClient).removeObject(any(RemoveObjectArgs.class));
    }
}
