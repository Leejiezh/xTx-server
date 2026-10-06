---
paths:
  - "**/mapper/**"
  - "**/*.xml"
  - "**/entity/**"
  - "**/*ServiceImpl.java"
---

# 数据库与 MyBatis-Plus

## 查询方式
- 单表简单增删改查，使用 MyBatis-Plus 自带方法；条件查询用 `LambdaQueryWrapper`，不要用字符串字段名。
- 两张表及以上的关联查询，直接写 SQL，不要堆一串 MyBatis-Plus 的 Lambda 方法。
- **SQL 统一放 XML mapper**（`src/main/resources/mapper/` 下同名 XML，namespace 对齐接口），不在 mapper 接口上用 `@Select`/`@Update` 等注解写 SQL：注解里写不了 resultMap、拆不了 `<sql>` 复用，长 SQL 在注解里可读性差。定制/多表查询一律走 XML。
- 禁止 `select *`，SQL 中明确列出字段。
- 禁止在循环中查库，批量操作用 `saveBatch`、`listByIds` 等。

## 分页
- 统一使用 MyBatis-Plus 分页插件，并限制最大页大小。

## 实体与表
- 实体统一包含 `id`、`create_time`、`update_time`、`deleted` 等公共字段，用自动填充处理。
- 表名、字段名使用下划线命名，布尔字段用 `is_` 前缀。

## 事务
- 多表写操作加 `@Transactional(rollbackFor = Exception.class)`。
- 事务方法不要在同类内部自调用（会导致事务失效）。
