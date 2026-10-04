package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.dto.UploadResp;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import com.leejie.xtx.core.mapper.UserMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplUploadTest {

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

    @Test
    @DisplayName("upload 拒绝白名单外的类型，字节不落 MinIO")
    void upload_rejectsUnsupportedType() {
        when(currentUser.currentUserId()).thenReturn(1L);
        var file = new MockMultipartFile("file", "hack.exe", "application/x-msdownload", new byte[]{0});

        BusinessException ex = assertThrows(BusinessException.class, () -> fileService.upload(file));

        assertEquals(422, ex.getCode());
        verifyNoInteractions(minioClient, fileMetadataMapper);
    }

    @Test
    @DisplayName("upload 拒绝超过文档上限的文件")
    void upload_rejectsOversizeDocument() {
        when(currentUser.currentUserId()).thenReturn(1L);
        // 用 mock 而非真造 50MB 字节数组：这里只关心 getSize() 的判定
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("application/pdf");
        when(file.getSize()).thenReturn(fileProperties.getDocumentMaxSize() + 1);

        assertEquals(422, assertThrows(BusinessException.class, () -> fileService.upload(file)).getCode());
        verifyNoInteractions(minioClient, fileMetadataMapper);
    }

    @Test
    @DisplayName("upload 拒绝超过图片上限的图片")
    void upload_rejectsOversizeImage() {
        when(currentUser.currentUserId()).thenReturn(1L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getSize()).thenReturn(fileProperties.getImageMaxSize() + 1);

        assertEquals(422, assertThrows(BusinessException.class, () -> fileService.upload(file)).getCode());
    }

    @Test
    @DisplayName("upload 成功：写入 MinIO、落 TEMP 元数据，objectKey 用 file/ 前缀")
    void upload_ok_putsObjectAndInsertsTemp() throws Exception {
        when(currentUser.currentUserId()).thenReturn(7L);
        when(minioConfig.getBucket()).thenReturn("xtx");
        byte[] data = "pdf-content".getBytes(StandardCharsets.UTF_8);
        var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", data);

        UploadResp resp = fileService.upload(file);

        ArgumentCaptor<PutObjectArgs> putCap = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCap.capture());
        assertEquals("xtx", putCap.getValue().bucket());
        assertEquals(resp.objectKey(), putCap.getValue().object());

        ArgumentCaptor<FileMetadata> metaCap = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileMetadataMapper).insert(metaCap.capture());
        FileMetadata meta = metaCap.getValue();
        assertEquals("TEMP", meta.getStatus());
        assertEquals(7L, meta.getUserId());
        assertEquals("doc.pdf", meta.getOriginalFilename());
        assertEquals("application/pdf", meta.getContentType());
        assertEquals((long) data.length, meta.getSize());

        assertTrue(resp.objectKey().startsWith("file/7/"), "非图片应落 file/ 前缀: " + resp.objectKey());
        assertTrue(resp.objectKey().endsWith(".pdf"));
        assertEquals((long) data.length, resp.size());
    }

    @Test
    @DisplayName("图片走代理上传也允许，但 objectKey 用 img/ 前缀（大小上限随之取图片那档）")
    void upload_image_usesImagePrefix() throws Exception {
        when(currentUser.currentUserId()).thenReturn(3L);
        when(minioConfig.getBucket()).thenReturn("xtx");
        var file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1, 2, 3});

        UploadResp resp = fileService.upload(file);

        verify(minioClient).putObject(any(PutObjectArgs.class));
        assertTrue(resp.objectKey().startsWith("img/3/"), resp.objectKey());
    }

    @Test
    @DisplayName("upload 拒绝缺失 content-type 的请求（按非图片分支走白名单，null 不在其中）")
    void upload_rejectsMissingContentType() {
        when(currentUser.currentUserId()).thenReturn(1L);
        MultipartFile file = mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(null);

        assertEquals(422, assertThrows(BusinessException.class, () -> fileService.upload(file)).getCode());
    }
}
