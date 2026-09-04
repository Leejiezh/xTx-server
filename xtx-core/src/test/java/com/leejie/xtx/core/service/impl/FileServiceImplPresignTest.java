package com.leejie.xtx.core.service.impl;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.dto.PresignReq;
import com.leejie.xtx.core.dto.PresignResp;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceImplPresignTest {

    @Mock
    private MinioClient minioClient;
    @Mock
    private MinioConfig minioConfig;
    @Mock
    private FileMetadataMapper fileMetadataMapper;
    @Mock
    private CurrentUserProvider currentUser;

    /** 用真实对象而非 mock：校验逻辑依赖它的默认上限值 */
    private final FileProperties fileProperties = new FileProperties();

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() {
        // 手动构造而非 @InjectMocks：fileProperties 不是 mock，@InjectMocks 会给它塞 null
        fileService = new FileServiceImpl(minioClient, minioConfig, fileMetadataMapper, fileProperties, currentUser);
    }

    private PresignReq req(String contentType, Long size, String filename) {
        return new PresignReq(contentType, size, filename);
    }

    @Test
    @DisplayName("presign 拒绝非白名单图片类型，且不落元数据、不碰 MinIO")
    void presign_rejectsUnsupportedType() {
        when(currentUser.currentUserId()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.presign(req("application/pdf", 100L, "a.pdf")));

        assertEquals(422, ex.getCode());
        verifyNoInteractions(fileMetadataMapper, minioClient);
    }

    @Test
    @DisplayName("presign 拒绝声明大小超限的图片")
    void presign_rejectsOversize() {
        when(currentUser.currentUserId()).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> fileService.presign(req("image/png", fileProperties.getImageMaxSize() + 1, "a.png")));

        assertEquals(422, ex.getCode());
        verifyNoInteractions(fileMetadataMapper, minioClient);
    }

    @Test
    @DisplayName("presign 拒绝非正数大小")
    void presign_rejectsNonPositiveSize() {
        when(currentUser.currentUserId()).thenReturn(1L);

        assertThrows(BusinessException.class, () -> fileService.presign(req("image/png", 0L, "a.png")));
    }

    @Test
    @DisplayName("presign 成功：返回 POST 表单与 objectKey，并落一条 TEMP 元数据")
    void presign_ok_returnsUrlAndKeyAndInsertsTemp() throws Exception {
        when(currentUser.currentUserId()).thenReturn(42L);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(minioConfig.getEndpoint()).thenReturn("http://localhost:9000");
        when(minioClient.getPresignedPostFormData(any(PostPolicy.class)))
                .thenReturn(new HashMap<>(Map.of("policy", "p", "x-amz-signature", "sig")));

        PresignResp resp = fileService.presign(req("image/jpeg", 1024L, "photo.jpg"));

        assertEquals("http://localhost:9000/xtx", resp.postUrl());
        assertNotNull(resp.expiresAt());
        assertTrue(resp.objectKey().startsWith("img/42/"), "objectKey 应以 img/{userId}/ 开头");
        assertTrue(resp.objectKey().endsWith(".jpg"));
        // SDK 返回的 formData 不含 key/Content-Type，后端须补齐才能通过 MinIO 表单校验
        assertEquals(resp.objectKey(), resp.formData().get("key"));
        assertEquals("image/jpeg", resp.formData().get("Content-Type"));
        assertEquals("p", resp.formData().get("policy"));

        ArgumentCaptor<PostPolicy> policyCap = ArgumentCaptor.forClass(PostPolicy.class);
        verify(minioClient).getPresignedPostFormData(policyCap.capture());
        assertEquals("xtx", policyCap.getValue().bucket());

        ArgumentCaptor<FileMetadata> metaCap = ArgumentCaptor.forClass(FileMetadata.class);
        verify(fileMetadataMapper).insert(metaCap.capture());
        FileMetadata meta = metaCap.getValue();
        assertEquals("TEMP", meta.getStatus());
        assertEquals(42L, meta.getUserId());
        assertEquals("photo.jpg", meta.getOriginalFilename());
        assertEquals("image/jpeg", meta.getContentType());
        assertEquals(1024L, meta.getSize());
        assertEquals(resp.objectKey(), meta.getObjectKey());
    }

    @Test
    @DisplayName("原始文件名里的斜杠不得泄进 objectKey，否则对象会被写到用户前缀之外")
    void presign_sanitizesExtensionFromHostileFilename() throws Exception {
        when(currentUser.currentUserId()).thenReturn(7L);
        when(minioConfig.getBucket()).thenReturn("xtx");
        when(minioConfig.getEndpoint()).thenReturn("http://localhost:9000");
        when(minioClient.getPresignedPostFormData(any(PostPolicy.class)))
                .thenReturn(new HashMap<>());

        PresignResp resp = fileService.presign(req("image/png", 10L, "evil.p/../../ng"));

        // img/7/yyyy/MM/dd/{uuid}{.ext} 恰好 6 段，多一段就说明扩展名带进了斜杠
        assertEquals(6, resp.objectKey().split("/").length, "objectKey 段数异常: " + resp.objectKey());
        assertTrue(resp.objectKey().startsWith("img/7/"));
    }
}
