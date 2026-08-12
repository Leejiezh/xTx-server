# MP 代码生成器设计文档

日期：2026-08-07
状态：已批准

> **变更记录：** 2026-08-12 生成器代码与 VM 模板迁移至独立模块 `xtx-code-generator`，包名由 `com.leejie.xtx.core.generator` 改为 `com.leejie.xtx.generator`；Entity 模板增加基类字段过滤与按需类型导入。生成产物仍输出到 `xtx-core`。

## 1. 目标

基于项目现有基类体系（BaseEntity / OwnedEntity / OwnedService / OwnedServiceImpl），生成一个适配项目规范的 MyBatis-Plus 自动代码生成器，包括 Velocity 模板文件。排除 user 表。

## 2. 整体架构

生成器作为独立 Maven 模块 `xtx-code-generator` 存在，依赖 `xtx-common`；生成的业务代码输出到 `xtx-core`。

### 2.1 生成器组件

```
xtx-code-generator/src/main/java/com/leejie/xtx/generator/
├── CodeGenerator.java       ← 主入口（main 方法），编排 FastAutoGenerator + DtoGenerator
└── DtoGenerator.java        ← 独立组件，JDBC + Velocity 生成 DTO/Controller
```

### 2.2 模板文件

```
xtx-code-generator/src/main/resources/templates/
├── entity.java.vm              ← Entity → 继承 OwnedEntity
├── service.java.vm             ← Service 接口
├── serviceImpl.java.vm         ← ServiceImpl → 继承 OwnedServiceImpl
├── mapper.java.vm              ← Mapper 接口
└── dto/
    ├── CreateReq.java.vm       ← 创建请求 DTO
    ├── UpdateReq.java.vm       ← 更新请求 DTO
    ├── VO.java.vm              ← 视图对象
    └── Controller.java.vm      ← REST Controller
```

### 2.3 生成方式

| 文件 | 基类 | 生成方式 |
|------|------|---------|
| XxxEntity.java | 继承 `OwnedEntity` | FastAutoGenerator + 自定义 VM |
| XxxMapper.java | 继承 `BaseMapper<XxxEntity>` | FastAutoGenerator + 自定义 VM |
| XxxMapper.xml | — | FastAutoGenerator 默认 |
| XxxService.java | 接口 | FastAutoGenerator + 自定义 VM |
| XxxServiceImpl.java | 继承 `OwnedServiceImpl<Mapper, Entity>` | FastAutoGenerator + 自定义 VM |
| XxxCreateReq.java | 纯 POJO | DtoGenerator |
| XxxUpdateReq.java | 纯 POJO | DtoGenerator |
| XxxVO.java | 纯 POJO | DtoGenerator |
| XxxController.java | 纯 POJO | DtoGenerator |

### 2.4 Entity 模板行为

- 跳过 `OwnedEntity` 已声明的基类字段（userId, createdAt, updatedAt, deleted），避免重复声明
- 按字段类型自动导入 `java.time.LocalDateTime` / `java.time.LocalDate` / `java.time.LocalTime` / `java.math.BigDecimal`

## 3. 生成产物结构（分层分包）

生成器将产物输出到业务模块 `xtx-core`：

```
xtx-core/src/main/java/com/leejie/xtx/core/
├── entity/          → XxxEntity.java
├── dto/             → XxxCreateReq.java, XxxUpdateReq.java, XxxVO.java
├── mapper/          → XxxMapper.java + XxxMapper.xml
├── service/         → XxxService.java
├── service/impl/    → XxxServiceImpl.java
└── controller/      → XxxController.java
```

## 4. DTO 字段映射规则

| DTO | 包含字段 | 排除字段 |
|-----|---------|---------|
| CreateReq | 所有业务字段 + 校验注解 | id, createdAt, updatedAt, userId, deleted |
| UpdateReq | id(必填) + 所有业务字段 + 校验注解 | createdAt, updatedAt, userId, deleted |
| VO | 全部字段 | deleted（不对外暴露） |

## 5. API 文档注解

依赖：`springdoc-openapi-starter-webmvc-ui` 2.7.0

| 文件 | 类注解 | 字段注解 |
|------|-------|---------|
| Entity | `@Schema(description = "xxx实体")` | 全部字段 `@Schema(description = "...")` |
| CreateReq | `@Schema(description = "xxx创建请求")` | 业务字段 `@Schema` + `@NotBlank`/`@NotNull` |
| UpdateReq | `@Schema(description = "xxx更新请求")` | 业务字段 `@Schema` + `@NotNull` |
| VO | `@Schema(description = "xxx视图对象")` | 全部字段 `@Schema` |
| Controller | `@Tag(name = "xxx管理")` | 方法 `@Operation(summary = "...")` |

## 6. DTO 转换方法

- **CreateReq.toEntity()** — BeanUtils.copyProperties 转换
- **UpdateReq.toEntity()** — BeanUtils.copyProperties 转换，含 id
- **VO.fromEntity()** — 静态工厂方法

## 7. Controller 模板

- RESTful 风格：POST（创建）、GET /{id}（详情）、PUT（更新）、DELETE /{id}（删除）、GET /page（分页）
- 返回统一响应体 `R<T>`
- 参数校验 `@Valid`
- `@RequiredArgsConstructor` 构造注入

## 8. 配置与使用

- 数据库连接信息在 CodeGenerator 中硬编码（开发环境专用工具类）
- 在 `xtx-code-generator` 模块中直接运行 `CodeGenerator.main()`（全限定名 `com.leejie.xtx.generator.CodeGenerator`）
- 支持反复运行（覆盖已存在文件）
- 增量生成：在 tables 数组中追加新表名重新运行

## 9. 依赖

生成器为独立模块，依赖关系如下：

`xtx-code-generator` 引入（springdoc 不在此处）：

```xml
<dependency>
    <groupId>com.leejie</groupId>
    <artifactId>xtx-common</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>
```

父 pom 新增依赖管理（`springdoc.version=2.7.0`）：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>
```

生成代码携带 `@Schema`/`@Tag`/`@Operation` 注解并编译进 `xtx-core`，故 `xtx-core` 仍引入 springdoc；分页插件 `PaginationInnerInterceptor` 需要 SQL 解析支持，因此 `xtx-core` 保留 `mybatis-plus-jsqlparser`：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-jsqlparser</artifactId>
</dependency>
```