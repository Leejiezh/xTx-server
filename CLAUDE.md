# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

微信小程序「AI 记录助手」的后端服务：随手记文字/拍照（生活/学习），AI 自动整理成日记、周记、学习总结、复盘等结构化内容。技术栈为 Java 21 + Spring Boot 3.4 + MyBatis-Plus + MySQL + Redis + MinIO + JWT + springdoc-openapi。

整体产品设计见 `docs/superpowers/specs/2026-08-05-ai-record-assistant-design.md`，实施计划见 `docs/superpowers/plans/2026-08-05-ai-record-assistant-plan.md`。

## 常用命令

环境：本地 Maven 3.9.9（无 mvnw wrapper）+ JDK 21。MySQL 库 `xtx` 的 DDL 见 `docs/sql/init.sql`；本地基础设施（MySQL8 + Redis）用 `cd docker && docker compose up -d` 启动。

```bash
# 构建全部模块并安装到本地仓库（运行任何启动模块前先执行）
mvn install -DskipTests

# 运行小程序端 API（端口 8080，context-path /api）
cd xtx-api && mvn spring-boot:run

# 运行管理后台（端口 8081）
cd xtx-admin && mvn spring-boot:run

# 全量测试
mvn test
```

各启动模块（xtx-api / xtx-admin）有 `@SpringBootTest` 冒烟测试；业务逻辑测试见各模块 `src/test`。

## 模块结构与依赖方向

```
xtx-api (小程序端 REST API 启动模块)  ─┐
                                      ├─> xtx-core (业务逻辑 + 数据访问)
xtx-admin (管理后台启动模块)          ─┘          │
                                                 ├─> xtx-common (基类/工具/异常/JWT/统一响应)
                                                 └─> xtx-wechat (微信集成：登录)
xtx-code-generator (代码生成器，独立工具模块，不参与部署) ─> xtx-common
```

- **xtx-common**：不依赖任何内部模块，承载整套基类体系（见下）。
- **xtx-wechat**：微信小程序集成，目前只有 `WechatLoginService` 接口 + `WechatConfig` 配置，登录实现与用户表 CRUD 尚未落地。
- **xtx-core**：业务实体/dto/mapper/service/controller 的所在地，含 MyBatis-Plus / Redis / MinIO 配置。`@MapperScan` 在 `MyBatisPlusConfig` 里（`com.leejie.xtx.core.**.mapper`）。
- **xtx-api**：依赖 spring-boot-starter-security，注册了 `JwtAuthInterceptor`。`@ComponentScan("com.leejie.xtx")` 全量扫描，保证 xtx-core/xtx-wechat 的 Bean 能被装配。
- **xtx-admin**：最小启动模块，仅一个 HealthController。

启动模块的 `application.yml` 中 MySQL/Redis/MinIO 连接信息为本地开发值（用户名密码是 leejie/123456，Redis 密码 123456）。

## 核心架构：所有权隔离（OwnedService）

**这是全项目最重要的设计，新增业务表时必须遵循。** 目标：用户只能读写自己的数据，越权在类型层面就写不出来，而不是靠 code review。

- `OwnedService<T extends OwnedEntity>`（xtx-common）的所有方法**都没有 userId 参数**——调用者拿不到「查别人数据」的表达能力。它是对 `OwnedServiceImpl` 的收口，后者是唯一实现。
- `OwnedServiceImpl` 统一处理：create 时**强制覆盖** userId（前端传谁的都不作数）；get/update/delete 先校验归属；不属于当前用户的 id 一律按「不存在」返回 **404**（不返回 403，避免泄露「记录存在但非你所有」而成为越权探测接口）。
- 逻辑删除：`deleted` 字段带 `@TableLogic`（配置在 application.yml），查询自动追加 `deleted = 0`。
- 分页查询把业务筛选条件**嵌套进 `and(...)` 括号内**，防止调用者用 `.or()` 逃出 `user_id = ?` 范围造成全站数据泄露。
- `OwnedServiceImpl` 用**字符串列名**（`"user_id"`、`"id"`）：真正原因是它是**泛型基类**，无法写 `T::getUserId` 方法引用，而非 lambda 解析失败。实测 MyBatis-Plus 3.5.9 下 `Record::getUserId`（getter 继承自 `OwnedEntity`）能正常解析为 `user_id` 列，前提是实体 TableInfo 已注册（Spring 启动时 mapper 扫描自动完成）。

`CurrentUserProvider` 接口是依赖方向的接缝：xtx-common 看不到 xtx-api 的登录态，所以定义该接口，由 xtx-api 的 `SecurityCurrentUserProvider`（读 Spring SecurityContextHolder）实现；测试则注入固定值假实现。

## 实体与 DTO 规范

- `BaseEntity`：`id`（自增主键）、`createdAt`、`updatedAt`。**时间戳由 MySQL 默认值生成**（DDL 里 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE`），项目里没有 MetaObjectHandler——因此 `save()` 之后实体对象里 `createdAt` 仍是 null，**新增接口只返回主键 id，不回填完整 VO**。
- `OwnedEntity extends BaseEntity`：`userId` + `deleted`（带 `@TableLogic`）。业务实体继承它。
- 实体子类用 Lombok 时必须加 `@EqualsAndHashCode(callSuper = true)`。
- 分页：请求继承 `PageQuery`（pageNum/pageSize，pageSize 上限 100，`toPage()` 兜默认值），响应用 `PageResult.of(ipage, conv)`——不直接序列化 MP 的 `IPage`，且带 `hasNext` 供小程序上拉加载。
- 统一响应 `R<T>`：`code/msg/data`，静态方法 `R.ok(...)` / `R.fail(...)`。
- 业务异常用 `BusinessException(code, msg)`，由 `GlobalExceptionHandler`（xtx-common 的 `@RestControllerAdvice`）统一转成 `R`。

## 代码生成器（xtx-code-generator）

为已有表生成全套 CRUD 代码，**自动适配上述基类体系**：Entity 继承 `OwnedEntity`、Service 继承 `OwnedService`、ServiceImpl 继承 `OwnedServiceImpl`。生成方案与字段映射规则见 `docs/superpowers/specs/2026-08-07-mp-code-generator-design.md`。

- `CodeGenerator.main()`：FastAutoGenerator 生成 Entity/Mapper/Service/ServiceImpl（xml 已禁用）；随后调用 `DtoGenerator.generate()` 用 JDBC + Velocity 生成 DTO/Controller。
- 模板在 `xtx-code-generator/src/main/resources/templates/`（entity/service/serviceImpl/mapper + `dto/` 下的 CreateReq/UpdateReq/VO/Controller）。
- **必须从项目根目录运行 `CodeGenerator.main()`**：输出路径基于 `System.getProperty("user.dir")`（`user.dir + "/xtx-core/src/main/java"`），在子目录运行会生成到错误位置。
- **会覆盖已存在的同名文件**；连接的是本地 MySQL（`root/123456`，库 `xtx`）。
- 明确**排除 user 表**（`TABLES = {"record", "report"}`，DtoGenerator 也跳过 user）。
- 生成后需要手动为新 Controller 补 `@Tag` 注解，并核对 DTO 校验注解是否满足业务要求。生成器只产出骨架，AI 生成报告等定制逻辑仍需手写。

## 当前未完成 / 易踩坑

- **Spring Security 认证链路未真正接通**：xtx-api 引入了 security 依赖、注册了 `JwtAuthInterceptor`，但**没有 SecurityConfig / JwtAuthFilter / UserController / 登录注册接口**。`SecurityUtils`（xtx-common 之外由 api 使用）从 SecurityContextHolder 取 userId，而其 javadoc 自述「Task 1 尚未实施」——当前 `JwtAuthInterceptor` 把 userId 放进 request attribute，与 SecurityUtils 的读取来源不一致。在实施认证链路前，接口实际无法完成登录态校验。
- `WechatLoginService` 只有接口没有实现；AI 生成（Spring AI + 通义千问）未实现；MinIO 上传未接业务。
- 数据库密码等敏感配置直接写死在 `application.yml` 与 `Constants.TOKEN_SECRET`（注释标记 change-in-production）——属已知取舍，部署前需外置。

--- 

## Spec 文档流程

- 编写 spec 文档时，允许默认执行简单、低耗时的 review，例如本地快速自检、spec self-review、轻量级 spec reviewer 检查。
- 普通 spec reviewer 如预期耗时较低、范围清晰，可以默认派发执行；若 review 范围较大、可能明显耗时，或需要额外外部资源/复杂验证，必须先征求用户确认。
- final review、final reviewer、正式评审闭环，以及任何耗时较高或影响范围较大的 review/验证操作，默认不要直接运行；仅在用户明确要求 "final review"、"正式 review"、"审核"、"检查 spec" 或类似指令时执行。
- 如果使用 superpowers 的 brainstorming / writing-plans 等流程，涉及 "用户 review spec"、"final review" 的强制环节默认跳过或简化；简单不耗时的 spec self-review / 轻量 review 可默认执行。

## 编码完成后的验证流程

- 编写完代码后，默认不要主动执行最终编译、打包、测试、启动服务、curl、SQL 人工验证等验证命令。
- 仅在用户明确要求"编译"、"测试"、"验证"、"运行"、"启动"、"打包"或指定具体命令时，才执行对应命令。
- 如果流程技能或计划中包含最终编译 / final verification / smoke test 等步骤，默认跳过，并在汇报中说明"按项目规则未执行验证命令"。
- 可以在最终汇报中给出建议用户自行执行的命令，但不要主动运行。