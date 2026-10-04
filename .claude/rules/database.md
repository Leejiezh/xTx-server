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

**重建本地库用 utf8mb4 连接**：容器内 mysql 客户端默认连接字符集是 **latin1**，直接 `mysql < init.sql` 会把 UTF-8 的中文按 latin1 存成双重编码乱码（表结构是 utf8mb4 也救不回）。
- 本机容器已修复默认：`/etc/my.cnf` 的 `[client]` 加了 `default-character-set=utf8mb4`（即时生效）；bind 挂载的 `/etc/mysql/conf.d/zz-charset.cnf` 加了 `[mysqld] skip-character-set-client-handshake`（容器重建后仍生效）。因此现在直接 `mysql < init.sql` 即可，不再需要显式标志。
- 若容器重建后这两处丢失，重加 `[mysqld] skip-character-set-client-handshake` 到 `/etc/mysql/conf.d/`（参考仓库 `docker/mysql/charset.cnf`）。
- 兜底命令仍有效：`wsl docker exec -i lee-mysql mysql --default-character-set=utf8mb4 -uroot -p123456 < /mnt/d/.../init.sql`。

**为什么 DDL 是权威而非附属文档**：项目没有 `MetaObjectHandler`，`created_at` / `updated_at` 的写入完全依赖 DDL 里的 `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`；`deleted` 的 `NOT NULL DEFAULT 0` 决定 `@TableLogic` 能否正常工作。改这些列的 DDL 会直接改变实体的运行时行为，而实体类里看不出任何线索。

## 5. 新增业务表的前提

要继承 `OwnedEntity` / `OwnedServiceImpl` 的所有权隔离，表必须包含 `id`、`user_id`、`deleted`、`created_at`、`updated_at`。缺列则无法继承——`user` 表就是这种例外（无 `user_id` / `deleted`，故代码生成器排除它）。

查询频繁的业务表建议按 `(user_id, 业务列, deleted)` 建复合索引，与现有两表保持一致。

## 6. 字段设计决策

### 6.1 `user.location` 存纯文本，不存行政区划代码

「所在地」是资料页展示性字段，无按地区筛选/统计诉求，因此存「省·市·区」纯文本（如 `浙江省·杭州市`），不引入 GB/T 2260 行政区划代码及省市区码表。

- **Why:** 维护约 3000+ 区县的国标码表（每年跟随更新）对一个个人记录类 app 是过度设计；且微信位置接口（`wx.getLocation` / `wx.chooseLocation`）受服务类目限制，日记记录类目无法申请，前端改用省市区三级选择器/手输。
- **How to apply:** 前端用三级地区选择器生成文本，后端不校验格式；若未来出现按地区运营/统计诉求，再迁移为代码列。

### 6.2 通用字典（dict_type / dict_item）是系统级共享数据，不继承 OwnedEntity

通用字典服务全项目（首个消费者是笔记标签 `note_label`），不属于任何用户，因此两表**无 `user_id`、无 `deleted`**，不继承 `OwnedEntity` / `OwnedService`，也没有越权问题。

- 删除语义由 `enabled` 开关承担（只禁用、不硬删），故不需要逻辑删除列。
- `dict_item.dict_type` 存 `dict_type.type_code` 字符串（不是数字 id）：查某本字典的项不用 join；代价是 `type_code` 被引用后不可改名。
- 类型专属属性（如标签颜色）存 `dict_item.extra`（JSON），不占通用列——避免"为一种类型污染通用表"。
- 读接口 `GET /api/dict/{typeCode}` 只读；增 / 改 / 启停用 SQL 手动维护。
