---
paths:
  - "**/api/**"
  - "**/dubbo/**"
  - "**/*Dubbo*.java"
  - "**/*Facade*.java"
---

# Dubbo 与微服务

- 服务接口定义在独立的 `api` 模块，只放接口和 DTO，不放实现。
- 入参出参必须实现 `Serializable`，并显式声明 `serialVersionUID`。
- 跨服务调用必须设置超时和重试策略，写操作默认不重试。
- Nacos 配置按环境隔离（namespace），敏感配置不入库、不入代码。
- 接口变更保持向后兼容：新增字段不删除旧字段；不兼容变更要升版本号。
