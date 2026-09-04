package com.leejie.xtx.api;

import com.leejie.xtx.common.base.security.CurrentUserProvider;
import com.leejie.xtx.core.config.FileProperties;
import com.leejie.xtx.core.config.MinioConfig;
import com.leejie.xtx.core.dto.PresignReq;
import com.leejie.xtx.core.dto.PresignResp;
import com.leejie.xtx.core.entity.FileMetadata;
import com.leejie.xtx.core.mapper.FileMetadataMapper;
import com.leejie.xtx.core.service.impl.FileServiceImpl;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 端到端：presign → 前端 POST 表单直传 MinIO → attach 校验真实大小 → accessUrl 取回 → delete。
 *
 * <p>需要本地 MinIO 运行（与 xtx-admin 的 MinioConnectivityTest 同一前提），
 * 但**不需要 MySQL**：file_metadata 用内存 Map 替身，因为这个测试要验证的是
 * 预签名表单真能被 HTTP 客户端直传/直取，而不是 MyBatis 映射。
 *
 * <p>只加载 {@link MinioConfig} + {@link FileProperties}，不启完整上下文
 * —— 完整上下文目前因认证链路未接通而起不来（既有问题，与文件服务无关）。
 */
@SpringBootTest(classes = {MinioConfig.class, FileProperties.class})
@EnableConfigurationProperties({MinioConfig.class, FileProperties.class})
class FileServiceFlowTest {

    private static final Long USER_ID = 1L;

    @Autowired
    private MinioClient minioClient;
    @Autowired
    private MinioConfig minioConfig;
    @Autowired
    private FileProperties fileProperties;

    /** 内存版 file_metadata，只支撑本测试用到的 insert / selectById / selectBatchIds / deleteById */
    private final Map<String, FileMetadata> rows = new ConcurrentHashMap<>();

    private FileServiceImpl fileService;

    @BeforeEach
    void setUp() throws Exception {
        // 新建的桶默认私有，正是 ADR-0002 要的状态
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build());
        }

        FileMetadataMapper mapper = mock(FileMetadataMapper.class);
        when(mapper.insert(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata meta = inv.getArgument(0);
            rows.put(meta.getObjectKey(), meta);
            return 1;
        });
        when(mapper.selectById(any())).thenAnswer(inv -> rows.get(String.valueOf(inv.getArgument(0))));
        when(mapper.selectBatchIds(any())).thenAnswer(inv -> {
            List<FileMetadata> found = new ArrayList<>();
            for (Object key : (Iterable<?>) inv.getArgument(0)) {
                FileMetadata meta = rows.get(String.valueOf(key));
                if (meta != null) {
                    found.add(meta);
                }
            }
            return found;
        });
        when(mapper.deleteById((Serializable) any())).thenAnswer(inv -> rows.remove(String.valueOf(inv.getArgument(0))) == null ? 0 : 1);
        when(mapper.updateById(any(FileMetadata.class))).thenAnswer(inv -> {
            FileMetadata patch = inv.getArgument(0);
            FileMetadata target = rows.get(patch.getObjectKey());
            target.setStatus(patch.getStatus());
            target.setRecordId(patch.getRecordId());
            target.setSize(patch.getSize());
            return 1;
        });

        CurrentUserProvider currentUser = () -> USER_ID;
        fileService = new FileServiceImpl(minioClient, minioConfig, mapper, fileProperties, currentUser);
    }

    @Test
    @DisplayName("预签名表单直传全流程：presign → POST → attach → accessUrl → delete")
    void presignedUploadRoundTrip() throws Exception {
        byte[] payload = "hello-minio".getBytes(StandardCharsets.UTF_8);

        PresignResp presign = fileService.presign(
                new PresignReq("image/jpeg", (long) payload.length, "封面.jpg"));
        assertNotNull(presign.postUrl());
        assertNotNull(presign.formData());
        assertTrue(presign.objectKey().startsWith("img/" + USER_ID + "/"), presign.objectKey());
        assertEquals(presign.objectKey(), presign.formData().get("key"));
        assertEquals("image/jpeg", presign.formData().get("Content-Type"));
        assertEquals("TEMP", rows.get(presign.objectKey()).getStatus());

        String boundary = "XtxBoundary" + System.nanoTime();
        byte[] multipart = buildMultipartBody(presign.formData(), "cover.jpg", payload, boundary);
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<Void> post = http.send(
                HttpRequest.newBuilder(URI.create(presign.postUrl()))
                        .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                        .POST(HttpRequest.BodyPublishers.ofByteArray(multipart))
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(200, post.statusCode(), "POST 表单直传应成功");

        fileService.attach(999L, List.of(presign.objectKey()));
        FileMetadata attached = rows.get(presign.objectKey());
        assertEquals("ATTACHED", attached.getStatus());
        // attach 回填的是 statObject 拿到的真实字节数，而非 presign 时前端自报的 size
        assertEquals((long) payload.length, attached.getSize());

        String url = fileService.accessUrl(presign.objectKey(), false);
        HttpResponse<byte[]> get = http.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, get.statusCode());
        assertEquals("hello-minio", new String(get.body(), StandardCharsets.UTF_8));

        fileService.delete(presign.objectKey());
        assertTrue(rows.isEmpty());
        assertThrows(Exception.class, () -> minioClient.statObject(StatObjectArgs.builder()
                .bucket(minioConfig.getBucket())
                .object(presign.objectKey())
                .build()), "对象应已从 MinIO 删除");
    }

    @Test
    @DisplayName("presign 之后不上传就 attach，应被真实性校验挡住")
    void attachWithoutUploadIsRejected() {
        PresignResp presign = fileService.presign(new PresignReq("image/png", 10L, "未上传.png"));

        assertEquals(422, assertThrows(com.leejie.xtx.common.exception.BusinessException.class,
                () -> fileService.attach(999L, List.of(presign.objectKey()))).getCode());
    }

    /** 按 S3 POST 表单拼 multipart body：formData 各字段作文本字段 + file 放二进制 */
    private static byte[] buildMultipartBody(Map<String, String> fields, String filename,
                                             byte[] payload, String boundary) {
        StringBuilder head = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            head.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(e.getKey()).append("\"\r\n\r\n")
                    .append(e.getValue()).append("\r\n");
        }
        head.append("--").append(boundary).append("\r\n")
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(filename).append("\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] headBytes = head.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);

        byte[] body = new byte[headBytes.length + payload.length + tailBytes.length];
        System.arraycopy(headBytes, 0, body, 0, headBytes.length);
        System.arraycopy(payload, 0, body, headBytes.length, payload.length);
        System.arraycopy(tailBytes, 0, body, headBytes.length + payload.length, tailBytes.length);
        return body;
    }
}
