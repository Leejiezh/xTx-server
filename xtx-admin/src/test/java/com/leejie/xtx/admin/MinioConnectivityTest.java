package com.leejie.xtx.admin;

import com.leejie.xtx.core.config.MinioConfig;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 聚焦 MinIO 上下文的连通性测试。
 * 只加载 {@link MinioConfig}（含 MinioClient Bean），不加载 xtx-admin 完整上下文
 * —— 完整上下文目前因缺少 CurrentUserProvider 实现而无法启动（既有 bug，与 MinIO 无关）。
 */
@SpringBootTest(classes = MinioConfig.class)
@EnableConfigurationProperties(MinioConfig.class)
class MinioConnectivityTest {

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    @Test
    @DisplayName("MinIO 配置真实连通性：bucket 确保存在 → 上传 → 校验 → 删除")
    void connectivity() throws Exception {
        String bucket = minioConfig.getBucket();
        String objectName = "minio-test/" + UUID.randomUUID() + ".txt";
        byte[] content = "hello-minio-连通性测试".getBytes(StandardCharsets.UTF_8);

        try {
            // 1. 配置健全性：防止 @ConfigurationProperties 未绑定
            assertNotNull(minioConfig.getEndpoint(), "endpoint 不能为空");
            assertNotNull(minioConfig.getAccessKey(), "accessKey 不能为空");
            assertNotNull(minioConfig.getSecretKey(), "secretKey 不能为空");
            assertNotNull(bucket, "bucket 不能为空");

            // 2. bucket 确保存在（幂等）
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            // 3. 上传
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType("text/plain")
                            .build());

            // 4. 校验：stat 确认存在且大小一致
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            assertEquals(content.length, stat.size(), "对象大小应与上传内容一致");

            // 4b. 校验：下载并比对内容
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
                byte[] downloaded = in.readAllBytes();
                assertArrayEquals(content, downloaded, "下载内容应与上传内容一致");
            }
        } finally {
            // 5. 清理：无论断言是否失败都删除测试对象
            try {
//                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
            } catch (Exception ignored) {
                // 清理失败不影响测试结果，避免掩盖原始断言错误
            }
        }
    }
}
