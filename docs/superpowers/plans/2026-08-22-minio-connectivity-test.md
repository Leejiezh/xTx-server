# MinIO 连通性测试 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 xtx-admin 模块新增一个 `@SpringBootTest` 连通性测试，验证用户配置的真实 MinIO 信息（endpoint/accessKey/secretKey/bucket）真实可用。

**Architecture:** 单个 `@SpringBootTest` 测试类 `MinioConnectivityTest`，注入 `MinioClient` 与 `MinioConfig` Bean（均来自 xtx-core 的 `MinioConfig`，通过 xtx-admin 的 `@ComponentScan(basePackages = {"com.leejie.xtx"})` 加载）。测试流程：配置健全性断言 → bucket 确保存在 → 上传唯一对象 → stat 校验 → 下载比对 → finally 中删除清理。失败时 MinIO SDK 异常直接抛出，由 JUnit 呈现。

**Tech Stack:** Java 21 · Spring Boot 3.4.0 · MinIO SDK 8.5.17 (`io.minio:minio`) · JUnit 5（`spring-boot-starter-test` 自带）

## Global Constraints

- 测试文件唯一位置：`xtx-admin/src/test/java/com/leejie/xtx/admin/MinioConnectivityTest.java`
- 不修改任何生产代码（含 `MinioConfig.java`）
- 不添加新 Maven 依赖（`spring-boot-starter-test` 已含 JUnit5 + `Assertions`）
- 遵循 xtx-admin 现有测试模式：`@SpringBootTest`（参考 `XTxAdminApplicationTests.java`）
- 配置来源：`xtx-admin/src/main/resources/application.yml` 的 `minio.*`（endpoint=http://localhost:9000, bucket=xtx）
- 前置条件：本地 MinIO 服务已启动（http://localhost:9000），且配置的 access-key/secret-key 有效

---

### Task 1: 编写并验证 MinIO 连通性测试

**Files:**
- Create: `xtx-admin/src/test/java/com/leejie/xtx/admin/MinioConnectivityTest.java`
- Test: 该文件本身即被测交付物（对真实 MinIO 执行往返操作）

**Interfaces:**
- Consumes: `com.leejie.xtx.core.config.MinioConfig`（Lombok `@Data`，提供 `getEndpoint()/getAccessKey()/getSecretKey()/getBucket()`）、`io.minio.MinioClient`（Spring Bean，由 `MinioConfig.minioClient()` 提供）
- Produces: 测试类 `MinioConnectivityTest`，单测试方法 `connectivity()`。使用 MinIO SDK 8.5.17 API：`bucketExists/makeBucket/putObject/statObject/getObject/removeObject`（Builder 模式）

- [ ] **Step 1: 验证前置条件 —— MinIO 服务可达**

运行：
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/minio/health/live
```
预期：输出 `200`。若不是 200，先启动本地 MinIO（如 `docker start <minio容器>` 或运行 docker/ 目录下的 MinIO 服务），再继续。

- [ ] **Step 2: 编写测试文件**

创建 `xtx-admin/src/test/java/com/leejie/xtx/admin/MinioConnectivityTest.java`：

```java
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
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
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
                minioClient.removeObject(
                        RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
            } catch (Exception ignored) {
                // 清理失败不影响测试结果，避免掩盖原始断言错误
            }
        }
    }
}
```

- [ ] **Step 3: 确保上游模块已安装到本地仓库**

首次运行前，先把 xtx-admin 依赖的内部模块（xtx-common / xtx-core / xtx-wechat）装入本地 Maven 仓库（跳过测试，因为 xtx-core 没有可独立运行的测试）：

```bash
mvn -pl xtx-common,xtx-core,xtx-wechat -am install -DskipTests
```
预期：`BUILD SUCCESS`。

- [ ] **Step 4: 运行测试，验证通过**

```bash
mvn -pl xtx-admin test -Dtest=MinioConnectivityTest
```
预期：`BUILD SUCCESS`，surefire 报告 `Tests run: 1, Failures: 0, Errors: 0`，且测试类显示为绿色。

若失败，按堆栈分类处理：
- `Connection refused` / 超时 → MinIO 服务未启动或 endpoint 配置错误
- `AccessDenied` / 凭证错误 → access-key/secret-key 配置错误
- `NoSuchBucket` 且创建失败 → bucket 权限不足

- [ ] **Step 5: 验证测试确实连的是真实服务（非假阳性）**

临时用错误 endpoint 覆盖配置（仅命令行参数，不改动 application.yml），测试应失败：

```bash
mvn -pl xtx-admin test -Dtest=MinioConnectivityTest -Dminio.endpoint=http://localhost:59999
```
预期：`BUILD FAILURE`，堆栈含 `Connection refused`。

然后再用正确配置重跑一次，确认恢复绿色：
```bash
mvn -pl xtx-admin test -Dtest=MinioConnectivityTest
```
预期：`BUILD SUCCESS`。

- [ ] **Step 6: 提交**

```bash
git add xtx-admin/src/test/java/com/leejie/xtx/admin/MinioConnectivityTest.java
git commit -m "test(minio): 新增 MinIO 配置连通性测试（上传→校验→删除）"
```
> 注意：只 add 测试文件。`git status` 中已有的未暂存改动（`xtx-admin/src/main/resources/application.yml`、`xtx-core/.../RecordCreateReq.java`、`xtx-api/.../HealthController.java` 删除）是用户进行中的工作，**不要**包含进本次提交。
