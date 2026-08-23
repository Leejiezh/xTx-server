# 数据库结构规范

本文件定义 xTx-server 项目查阅与维护数据库结构的方式，编写或修改涉及数据表的代码时**必须遵守**。

## 1. 本文件的维护约定

数据库相关的**约定与决策**一旦产生，就主动追加到本文件，不必等人提醒。来源包括：

- 用户明确提出的数据库相关要求
- 实现业务需求过程中做出的选择（为什么这样建索引、为什么某字段允许 NULL、为什么某表不继承 `OwnedEntity`）
- 踩过的坑及其规避方式

**只记约定与决策。** 字段名、类型、索引定义等结构事实只更新 `init.sql`（见第 4 节），本文件不重复——同一事实在两处维护，必然走向不一致，且读者无从判断该信哪个。

追加时归入下列已有小节；确实没有合适小节再新建，避免本文件退化成无序堆叠的流水账。

## 2. `docs/sql/init.sql` 是全库 DDL 的唯一来源

实体类只表达字段名和 Java 类型。以下信息**只存在于 init.sql**，需要时去那里查，不要靠猜：

- NULL / NOT NULL 约束
- 索引与唯一约束（含复合索引的列顺序）
- 字段长度与类型（`TEXT` / `LONGTEXT` / `JSON` 的区别）
- 默认值
- 字符集与排序规则
- 表和列的 COMMENT

例：`record` 与 `report` 的索引都是复合索引 `(user_id, record_date, deleted)` / `(user_id, created_at, deleted)`，实体类里完全看不出来，但直接决定查询能否命中索引。

## 3. 查阅方式：按需取用

- 当前文件约 70 行 / 3 张表（`user`、`record`、`report`），需要时可直接整读。
- **文件超过约 300 行或 10 张表后**，改为按表定位：先搜 ``CREATE TABLE.*`表名` `` 拿到起始行，再读该表所在区间，不整读全文。

## 4. 变更同步：DDL 是权威，实体类跟随

实体类字段增删改、或新增表时，**在同一次改动内**同步更新 init.sql —— 直接改写对应的 `CREATE TABLE` 语句，让它始终等于当前最新全量结构，**不追加 `ALTER TABLE`**。本地库结构变更通过重建对齐。

> 一旦有生产数据，此条需重新评估，改为独立 migration 脚本。

**为什么 DDL 是权威而非附属文档**：项目没有 `MetaObjectHandler`，`created_at` / `updated_at` 的写入完全依赖 DDL 里的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`；`deleted` 的 `NOT NULL DEFAULT 0` 决定 `@TableLogic` 能否正常工作。改这些列的 DDL 会直接改变实体的运行时行为，而实体类里看不出任何线索。

## 5. 新增业务表的前提

要继承 `OwnedEntity` / `OwnedServiceImpl` 的所有权隔离，表必须包含 `id`、`user_id`、`deleted`、`created_at`、`updated_at`。缺列则无法继承——`user` 表就是这种例外（无 `user_id` / `deleted`，故代码生成器排除它）。

查询频繁的业务表建议按 `(user_id, 业务列, deleted)` 建复合索引，与现有两表保持一致。
