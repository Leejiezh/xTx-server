# MinIO 连通性测试设计

日期：2026-08-22
状态：已确认

## 背景

`MinioConfig`（`xtx-core/src/main/java/com/leejie/xtx/core/config/MinioConfig.java`）通过 `@ConfigurationProperties(prefix = "minio")` 绑定 `endpoint/accessKey/secretKey/bucket`，并提供 `MinioClient` Bean。用户在 `xtx-admin/src/main/resources/application.yml` 中配置了真实的 MinIO 信息，需要编写一个单元测试验证该配置真实可用。

## 目标

- 验证 xtx-admin 模块中配置的 MinIO 信息（endpoint、凭证、bucket）真实可用
- 通过真实的连通性往返（上传→读取→删除）端到端验证配置
- 不修改任何生产代码，不引入新依赖

## 方案

单个 `@SpringBootTest` 连通性测试（方案 A）。

### 文件位置

`xtx-admin/src/test/java/com/leejie/xtx/admin/MinioConnectivityTest.java`

### 测试类结构

```java
@SpringBootTest
class MinioConnectivityTest {
    @Autowired MinioClient minioClient;
    @Value("${minio.bucket}") String bucket;
}
```

### 测试流程（单个测试方法 `connectivity()`）

1. **配置健全性**：断言 `endpoint`、`accessKey` 等配置非空，防止配置未绑定
2. **bucket 确保存在**：`bucketExists` 检查，不存在则 `makeBucket`（幂等）
3. **上传**：唯一对象名（`minio-test/<uuid>.txt`），`putObject` 写入测试字节 + `contentType=text/plain`
4. **校验**：`statObject` 确认对象存在且 size 匹配 → `getObject` 读取内容并断言与上传内容一致
5. **清理**：`removeObject` 删除对象，使用 `try/finally` 保证即使断言失败也清理，不遗留垃圾对象

### 错误处理

MinIO SDK 异常（连接失败、凭证错误、bucket 权限问题）直接向外抛出，JUnit 展示堆栈，测试即失败。这是连通性测试的预期行为。

### 验证方式

- 前置条件：本地 MinIO 服务已启动且凭证正确
- 命令：`mvn -pl xtx-admin test`（或 `mvn test -Dtest=MinioConnectivityTest`）

## 明确不做

- 不引入 Testcontainers / Docker 依赖
- 不修改 `MinioConfig` 或任何生产代码
- 不添加新 Maven 依赖（`spring-boot-starter-test` 已包含 JUnit5 + 断言）
- 不做多测试方法拆分，单个连通性测试即可覆盖

## 测试/校验计划

运行 `mvn -pl xtx-admin test`，观察 `MinioConnectivityTest` 通过。若失败，根据堆栈判断是连接失败、凭证错误还是 bucket 权限问题。
