# 通用字典（Dictionary）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把写死的记录分类（`LIFE`/`STUDY`）替换为数据驱动的通用字典（`dict_type` + `dict_item`），首个消费者是笔记标签 `note_label`。

**Architecture:** 两张系统级表（不继承 `OwnedEntity`，无 `user_id`/`deleted`）；`dict_item.extra` 用 JSON 列承载类型专属属性（如标签颜色），通用列不因某一种字典而膨胀；对外只暴露一个只读接口 `GET /api/dict/{typeCode}`，写操作走 SQL。`record.category` 改名为 `record.label`（可空，NULL=未分类），存 `dict_item.item_key`。

**Tech Stack:** Java 21、Spring Boot 3.4、MyBatis-Plus 3.5.9、MySQL 8、springdoc-openapi、JUnit 5 + Mockito + AssertJ。

**Spec:** `docs/superpowers/specs/2026-10-04-generic-dict-design.md`

## Global Constraints

- 主键统一**雪花 ID**：实体继承 `BaseEntity`（`@TableId(type = IdType.ASSIGN_ID)`），DDL 里 `id BIGINT NOT NULL` **无 AUTO_INCREMENT**。
- 时间戳由 **MySQL 默认值**生成（`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP`），项目**无** `MetaObjectHandler`；`save()` 后实体里 `createdAt` 仍为 null。
- 实体子类用 Lombok 必须 `@EqualsAndHashCode(callSuper = true)`。
- 分层：**实体 / Mapper / Service / DTO 在 `xtx-core`，Controller 在 `xtx-api`**（与 `RecordController`+`RecordService` 的分布一致）。
- 统一响应 `com.leejie.xtx.common.result.R<T>`（`R.ok(data)` / `getData()`）。
- 字典两表**不继承 `OwnedEntity`**（无 `user_id`、无 `deleted`）；**不要**给它们加 `user_id`。
- JSON 序列化只用 **Jackson**（`com.fasterxml.jackson`），不用 Hutool JSON。
- Java 21 新特性优先：`List.getFirst()`、`stream().toList()`、`List.of(...)`。
- 注释只写 WHY，不写 WHAT。
- 提交信息**中文、带前缀**（`feat(...)` / `docs(...)` / `test(...)`）。
- **项目规则**：默认**不主动执行**编译 / 测试 / 建库 / 启动 / curl 等验证命令；仅在用户明确要求时执行，并在汇报中说明"按项目规则未执行验证"。本计划中所有"Run:"步骤默认跳过，留给用户按需运行。

## Review Focus

以下输入/失败模式是 spec 隐含但代码不显式处理的，最容易出问题：

1. **未知 `typeCode`**（前端拼错 / 类型未注册）→ 必须返回空数组，不能 500、不能抛异常。
2. **类型存在但整本被停用**（`dict_type.enabled = 0`）→ 必须返回空数组，而不是照常返回其项。
3. **单条 item 被停用**（`dict_item.enabled = 0`）→ 不得出现在列表里。
4. **把记录标签改回"未分类"（`label = null`）** → 必须真正写空；默认 `NOT_NULL` 更新策略会静默跳过 null，旧标签删不掉。
5. **`extra` 为 NULL / 脏 JSON** → NULL 还原成 null；非法 JSON 抛 `SQLException`（可被 MyBatis 包成 `DataAccessException`），不能让接口 500 崩溃。
6. **排序**：按 `sort_order` 升序返回。

---

### Task 1: init.sql —— 字典两表 + seed

**Files:**
- Modify: `docs/sql/init.sql`（在 `file_metadata` 之后追加第 5、6 节）

**Interfaces:**
- Produces: 表 `dict_type`、`dict_item`；seed 类型 `note_label` 与 4 个项（work/design/tech/life，颜色进 `extra`）。

- [ ] **Step 1: 追加字典两表建表语句**

在 `docs/sql/init.sql` 末尾（`file_metadata` 之后）追加：

```sql
-- ========================================
-- 5. 字典类型表（类型目录：全项目有哪些字典）
--
-- 系统级共享数据，不继承 OwnedEntity：无 user_id、无 deleted。
-- 删除语义由 enabled 开关承担（只禁用、不硬删），故不需要逻辑删除列。
-- ========================================
CREATE TABLE IF NOT EXISTS `dict_type` (
    `id`         BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `type_code`  VARCHAR(64)  NOT NULL                 COMMENT '类型码，如 note_label',
    `type_name`  VARCHAR(128) NOT NULL                 COMMENT '类型名称，如 笔记标签',
    `remark`     VARCHAR(255) DEFAULT NULL             COMMENT '备注',
    `enabled`    TINYINT      NOT NULL DEFAULT 1       COMMENT '整本字典启用(0-否,1-是)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';

-- ========================================
-- 6. 字典项表（字典内容：每本里有哪些项）
--
-- dict_type 列存 dict_type.type_code 字符串（非数字 id）：查某本字典的项不用 join，
-- 代价是 type_code 一旦被引用就不可改名（靠约定与 SQL 维护自觉遵守）。
-- extra 存类型专属属性（如标签颜色），不占通用列。
-- ========================================
CREATE TABLE IF NOT EXISTS `dict_item` (
    `id`         BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `dict_type`  VARCHAR(64)  NOT NULL                 COMMENT '所属字典类型码(= dict_type.type_code)',
    `item_key`   VARCHAR(64)  NOT NULL                 COMMENT '项键(机器值，业务表引用它)',
    `item_label` VARCHAR(128) NOT NULL                 COMMENT '展示名',
    `sort_order` INT          NOT NULL DEFAULT 0       COMMENT '排序(升序)',
    `enabled`    TINYINT      NOT NULL DEFAULT 1       COMMENT '启用(0-否,1-是)',
    `remark`     VARCHAR(255) DEFAULT NULL             COMMENT '备注',
    `extra`      JSON         DEFAULT NULL             COMMENT '类型专属属性，如 {"color":"#7C3AED"}',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_key` (`dict_type`, `item_key`),
    KEY `idx_type_enabled_sort` (`dict_type`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项表';
```

- [ ] **Step 2: 追加 seed（可重复执行）**

紧接上面追加。用 `ON DUPLICATE KEY UPDATE` 而非裸 `INSERT`，保证 init.sql 可重复执行：

```sql
-- ========================================
-- 7. 字典 seed：首个字典类型 note_label（笔记标签）
--
-- "未分类"不是字典项 —— 它是 record.label 为 NULL 的状态，故这里只有 4 项。
-- ========================================
INSERT INTO `dict_type` (`id`, `type_code`, `type_name`, `remark`) VALUES
    (1, 'note_label', '笔记标签', '笔记(record)的分类标签')
ON DUPLICATE KEY UPDATE `type_name` = VALUES(`type_name`);

INSERT INTO `dict_item` (`id`, `dict_type`, `item_key`, `item_label`, `sort_order`, `extra`) VALUES
    (1, 'note_label', 'work',   '工作', 10, JSON_OBJECT('color', '#7C3AED')),
    (2, 'note_label', 'design', '设计', 20, JSON_OBJECT('color', '#EC4899')),
    (3, 'note_label', 'tech',   '技术', 30, JSON_OBJECT('color', '#3B82F6')),
    (4, 'note_label', 'life',   '生活', 40, JSON_OBJECT('color', '#F59E0B'))
ON DUPLICATE KEY UPDATE `item_label` = VALUES(`item_label`), `extra` = VALUES(`extra`);
```

- [ ] **Step 3: 建库验证（按项目规则默认不执行，用户要求时再跑）**

```bash
mysql -uroot -p123456 < docs/sql/init.sql
mysql -uroot -p123456 xtx -e "SHOW TABLES; SELECT item_key,item_label,extra FROM dict_item ORDER BY sort_order;"
```
预期：共 6 张表（含 `dict_type`、`dict_item`）；`dict_item` 4 行，`extra` 为 `{"color": "#..."}`。

- [ ] **Step 4: Commit**

```bash
git add docs/sql/init.sql
git commit -m "feat(dict): 新增通用字典两表与 note_label seed"
```

---

### Task 2: `JsonMapTypeHandler`（`dict_item.extra` 的 JSON 转换器）

**Files:**
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/handler/JsonMapTypeHandler.java`
- Test: `xtx-core/src/test/java/com/leejie/xtx/core/handler/JsonMapTypeHandlerTest.java`

**Interfaces:**
- Produces: `com.leejie.xtx.core.handler.JsonMapTypeHandler`（`BaseTypeHandler<Map<String,Object>>`），供 `DictItem.extra` 的 `@TableField(typeHandler = ...)` 使用。

- [ ] **Step 1: 写失败测试**

创建 `JsonMapTypeHandlerTest.java`（镜像既有 `JsonListTypeHandlerTest` 的风格）：

```java
package com.leejie.xtx.core.handler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonMapTypeHandlerTest {

    private final JsonMapTypeHandler handler = new JsonMapTypeHandler();

    @Test
    @DisplayName("写库：Map 序列化为 JSON 对象字符串")
    void setNonNullParameter_writesJsonObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, Map.of("color", "#7C3AED"), JdbcType.VARCHAR);
        verify(ps).setString(1, "{\"color\":\"#7C3AED\"}");
    }

    @Test
    @DisplayName("读库：JSON 对象还原为 Map")
    void getNullableResult_parsesJsonObject() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("{\"color\":\"#7C3AED\"}");
        assertEquals(Map.of("color", "#7C3AED"), handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：NULL 列还原为 null（extra 可选，与 images 的空数组语义不同）")
    void getNullableResult_null_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn(null);
        assertNull(handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：空白字符串同样还原为 null")
    void getNullableResult_blank_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("  ");
        assertNull(handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：按列下标与 CallableStatement 两条路径同样可用")
    void getNullableResult_byIndex_andCallable() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("{\"color\":\"#fff\"}");
        assertEquals(Map.of("color", "#fff"), handler.getNullableResult(rs, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn("{\"color\":\"#000\"}");
        assertEquals(Map.of("color", "#000"), handler.getNullableResult(cs, 1));
    }

    @Test
    @DisplayName("读库：脏数据抛 SQLException，而非 RuntimeException")
    void getNullableResult_malformed_throwsSqlException() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("not-json");
        assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "extra"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=JsonMapTypeHandlerTest`
Expected: 编译失败 —— `JsonMapTypeHandler` 不存在。

- [ ] **Step 3: 实现 handler**

创建 `JsonMapTypeHandler.java`：

```java
package com.leejie.xtx.core.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * {@code Map<String,Object>} 与 JSON 对象字符串之间的类型转换器，用于 DictItem.extra
 * （MySQL JSON 列，存类型专属属性，如 {"color":"#7C3AED"}）。
 *
 * <p>NULL 与空白还原成 null（表示"无类型专属属性"），而非空 Map —— extra 是可选的，
 * 与 JsonListTypeHandler 的"空数组"语义相反。
 *
 * <p>只在字段上用 {@code @TableField(typeHandler = ...)} 显式指定，因此实体所在的
 * {@code @TableName} 必须带 {@code autoResultMap = true}，否则查询结果映射不会走本转换器。
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonMapTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> TYPE = new TypeReference<>() {
    };

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setString(i, MAPPER.writeValueAsString(parameter));
        } catch (Exception e) {
            throw new SQLException("序列化 Map<String,Object> 失败", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, TYPE);
        } catch (Exception e) {
            throw new SQLException("反序列化 Map<String,Object> 失败: " + json, e);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=JsonMapTypeHandlerTest`
Expected: PASS（6 个用例）。

- [ ] **Step 5: Commit**

```bash
git add xtx-core/src/main/java/com/leejie/xtx/core/handler/JsonMapTypeHandler.java \
        xtx-core/src/test/java/com/leejie/xtx/core/handler/JsonMapTypeHandlerTest.java
git commit -m "feat(dict): 新增 Map JSON 类型转换器 JsonMapTypeHandler"
```

---

### Task 3: 字典实体 + Mapper + `DictService`

**Files:**
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/entity/DictType.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/entity/DictItem.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/mapper/DictTypeMapper.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/mapper/DictItemMapper.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/service/DictService.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/service/impl/DictServiceImpl.java`
- Test: `xtx-core/src/test/java/com/leejie/xtx/core/service/impl/DictServiceImplTest.java`

**Interfaces:**
- Consumes: `JsonMapTypeHandler`（Task 2）。
- Produces:
  - `com.leejie.xtx.core.entity.DictType extends BaseEntity`（字段 `typeCode` / `typeName` / `remark` / `enabled`）
  - `com.leejie.xtx.core.entity.DictItem extends BaseEntity`（字段 `dictType` / `itemKey` / `itemLabel` / `sortOrder` / `enabled` / `remark` / `extra: Map<String,Object>`）
  - `com.leejie.xtx.core.service.DictService#listEnabledItems(String typeCode) : List<DictItem>`

- [ ] **Step 1: 写失败测试**

创建 `DictServiceImplTest.java`：

```java
package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.segments.MergeSegments;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.entity.DictType;
import com.leejie.xtx.core.mapper.DictItemMapper;
import com.leejie.xtx.core.mapper.DictTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DictServiceImplTest {

    @Mock
    private DictTypeMapper dictTypeMapper;
    @Mock
    private DictItemMapper dictItemMapper;

    private DictServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DictServiceImpl(dictTypeMapper, dictItemMapper);
        // 手动注册 TableInfo，让 LambdaQueryWrapper 的方法引用能解析成列名（等价于 Spring 启动时的 mapper 扫描）
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), DictItem.class);
    }

    private static DictType type(int enabled) {
        DictType t = new DictType();
        t.setTypeCode("note_label");
        t.setEnabled(enabled);
        return t;
    }

    @Test
    @DisplayName("类型不存在 → 空列表，且不查 item")
    void unknownType_returnsEmpty() {
        when(dictTypeMapper.selectOne(any())).thenReturn(null);

        assertThat(service.listEnabledItems("nope")).isEmpty();
        verify(dictItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("类型整本停用 → 空列表，且不查 item")
    void disabledType_returnsEmpty() {
        when(dictTypeMapper.selectOne(any())).thenReturn(type(0));

        assertThat(service.listEnabledItems("note_label")).isEmpty();
        verify(dictItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("类型启用 → 返回 item，查询带 enabled 过滤与 sort_order 排序")
    void enabledType_returnsItemsWithFilterAndOrder() {
        when(dictTypeMapper.selectOne(any())).thenReturn(type(1));
        DictItem work = new DictItem();
        work.setItemKey("work");
        when(dictItemMapper.selectList(any())).thenReturn(List.of(work));

        assertThat(service.listEnabledItems("note_label")).containsExactly(work);

        ArgumentCaptor<LambdaQueryWrapper<DictItem>> cap = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(dictItemMapper).selectList(cap.capture());
        LambdaQueryWrapper<DictItem> wrapper = cap.getValue();
        assertThat(wrapper.getSqlSegment()).contains("enabled");
        MergeSegments expr = wrapper.getExpression();
        assertThat(expr.getOrderBy().toString()).contains("sort_order");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=DictServiceImplTest`
Expected: 编译失败 —— `DictService` / `DictServiceImpl` / `DictItem` 等不存在。

- [ ] **Step 3: 实现实体**

`DictType.java`：

```java
package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型表（类型目录：全项目有哪些字典）。
 *
 * <p>系统级共享数据，不继承 OwnedEntity —— 无 userId、无 deleted，删除语义由 enabled 承担。
 */
@Schema(description = "字典类型表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("dict_type")
public class DictType extends BaseEntity {

    @Schema(description = "类型码，如 note_label")
    private String typeCode;

    @Schema(description = "类型名称，如 笔记标签")
    private String typeName;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "整本字典启用(0-否,1-是)")
    private Integer enabled;
}
```

`DictItem.java`：

```java
package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.leejie.xtx.common.base.entity.BaseEntity;
import com.leejie.xtx.core.handler.JsonMapTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 字典项表（字典内容）。
 *
 * <p>autoResultMap = true 是 extra 自定义 TypeHandler 生效的前提（与 Record.images 同理）。
 */
@Schema(description = "字典项表")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "dict_item", autoResultMap = true)
public class DictItem extends BaseEntity {

    @Schema(description = "所属字典类型码")
    private String dictType;

    @Schema(description = "项键(机器值)")
    private String itemKey;

    @Schema(description = "展示名")
    private String itemLabel;

    @Schema(description = "排序")
    private Integer sortOrder;

    @Schema(description = "启用(0-否,1-是)")
    private Integer enabled;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "类型专属属性，如 {\"color\":\"#7C3AED\"}")
    @TableField(typeHandler = JsonMapTypeHandler.class)
    private Map<String, Object> extra;
}
```

- [ ] **Step 4: 实现 Mapper**

`DictTypeMapper.java`：

```java
package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leejie.xtx.core.entity.DictType;

/** 字典类型表 Mapper */
public interface DictTypeMapper extends BaseMapper<DictType> {
}
```

`DictItemMapper.java`：

```java
package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leejie.xtx.core.entity.DictItem;

/** 字典项表 Mapper */
public interface DictItemMapper extends BaseMapper<DictItem> {
}
```

- [ ] **Step 5: 实现 Service**

`DictService.java`：

```java
package com.leejie.xtx.core.service;

import com.leejie.xtx.core.entity.DictItem;

import java.util.List;

/**
 * 通用字典读服务。
 *
 * <p>字典是系统级共享数据，不属于任何用户，因此不继承 OwnedService、没有 userId 概念。
 */
public interface DictService {

    /** 取某本字典的启用项，按 sort_order 升序；类型不存在或整本停用时返回空列表 */
    List<DictItem> listEnabledItems(String typeCode);
}
```

`DictServiceImpl.java`：

```java
package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.entity.DictType;
import com.leejie.xtx.core.mapper.DictItemMapper;
import com.leejie.xtx.core.mapper.DictTypeMapper;
import com.leejie.xtx.core.service.DictService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final int ENABLED = 1;

    private final DictTypeMapper dictTypeMapper;
    private final DictItemMapper dictItemMapper;

    @Override
    public List<DictItem> listEnabledItems(String typeCode) {
        DictType type = dictTypeMapper.selectOne(
                new LambdaQueryWrapper<DictType>().eq(DictType::getTypeCode, typeCode));
        // 类型不存在或整本停用，都不该把项暴露出去
        if (type == null || !Integer.valueOf(ENABLED).equals(type.getEnabled())) {
            return List.of();
        }
        return dictItemMapper.selectList(new LambdaQueryWrapper<DictItem>()
                .eq(DictItem::getDictType, typeCode)
                .eq(DictItem::getEnabled, ENABLED)
                .orderByAsc(DictItem::getSortOrder)
                .orderByAsc(DictItem::getId));
    }
}
```

- [ ] **Step 6: 运行测试确认通过**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=DictServiceImplTest`
Expected: PASS（3 个用例）。

- [ ] **Step 7: Commit**

```bash
git add xtx-core/src/main/java/com/leejie/xtx/core/entity/DictType.java \
        xtx-core/src/main/java/com/leejie/xtx/core/entity/DictItem.java \
        xtx-core/src/main/java/com/leejie/xtx/core/mapper/DictTypeMapper.java \
        xtx-core/src/main/java/com/leejie/xtx/core/mapper/DictItemMapper.java \
        xtx-core/src/main/java/com/leejie/xtx/core/service/DictService.java \
        xtx-core/src/main/java/com/leejie/xtx/core/service/impl/DictServiceImpl.java \
        xtx-core/src/test/java/com/leejie/xtx/core/service/impl/DictServiceImplTest.java
git commit -m "feat(dict): 新增字典实体/Mapper 与只读 DictService"
```

---

### Task 4: `DictItemVO` + `DictController`（读接口）

**Files:**
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/dto/DictItemVO.java`
- Create: `xtx-api/src/main/java/com/leejie/xtx/api/controller/DictController.java`
- Test: `xtx-api/src/test/java/com/leejie/xtx/api/controller/DictControllerTest.java`

**Interfaces:**
- Consumes: `DictService#listEnabledItems(String)`（Task 3）。
- Produces:
  - `com.leejie.xtx.core.dto.DictItemVO`（字段 `key` / `label` / `sortOrder` / `extra`；静态工厂 `fromEntity(DictItem)`）
  - `GET /api/dict/{typeCode}` → `R<List<DictItemVO>>`

- [ ] **Step 1: 写失败测试**

创建 `DictControllerTest.java`：

```java
package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.DictItemVO;
import com.leejie.xtx.core.entity.DictItem;
import com.leejie.xtx.core.service.DictService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DictControllerTest {

    @Test
    @DisplayName("把字典实体映射成 {key,label,sortOrder,extra} 并包进 R")
    void list_mapsEntityToVo() {
        DictService dictService = mock(DictService.class);
        DictItem work = new DictItem();
        work.setItemKey("work");
        work.setItemLabel("工作");
        work.setSortOrder(10);
        work.setExtra(Map.of("color", "#7C3AED"));
        when(dictService.listEnabledItems("note_label")).thenReturn(List.of(work));

        R<List<DictItemVO>> r = new DictController(dictService).list("note_label");

        assertEquals(200, r.getCode());
        DictItemVO vo = r.getData().getFirst();
        assertEquals("work", vo.getKey());
        assertEquals("工作", vo.getLabel());
        assertEquals(10, vo.getSortOrder());
        assertEquals("#7C3AED", vo.getExtra().get("color"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-api test -Dtest=DictControllerTest`
Expected: 编译失败 —— `DictItemVO` / `DictController` 不存在。

- [ ] **Step 3: 实现 VO**

`DictItemVO.java`：

```java
package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.DictItem;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "字典项视图对象")
@Data
public class DictItemVO {

    @Schema(description = "项键")
    private String key;

    @Schema(description = "展示名")
    private String label;

    @Schema(description = "排序")
    private Integer sortOrder;

    /** 类型专属属性原样透传，前端自行读取（如 extra.color），不在后端拍平 */
    @Schema(description = "类型专属属性")
    private Map<String, Object> extra;

    public static DictItemVO fromEntity(DictItem entity) {
        DictItemVO vo = new DictItemVO();
        vo.setKey(entity.getItemKey());
        vo.setLabel(entity.getItemLabel());
        vo.setSortOrder(entity.getSortOrder());
        vo.setExtra(entity.getExtra());
        return vo;
    }
}
```

- [ ] **Step 4: 实现 Controller**

`DictController.java`：

```java
package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.DictItemVO;
import com.leejie.xtx.core.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 通用字典读接口。字典是系统级共享数据，任何已登录用户可读。 */
@Tag(name = "字典")
@RestController
@RequestMapping("/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/{typeCode}")
    @Operation(summary = "按类型码查询字典项(仅启用项)")
    public R<List<DictItemVO>> list(@PathVariable String typeCode) {
        return R.ok(dictService.listEnabledItems(typeCode).stream()
                .map(DictItemVO::fromEntity)
                .toList());
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-api test -Dtest=DictControllerTest`
Expected: PASS。

- [ ] **Step 6: 接口冒烟（按项目规则默认不执行，用户要求时再跑）**

启动 `cd xtx-api && mvn spring-boot:run`，带登录拿到的 JWT 调：
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/dict/note_label
```
预期：`data` 为 4 项（work/design/tech/life），每项含 `key`/`label`/`sortOrder`/`extra.color`。调 `GET /api/dict/unknown` 预期 `data: []`。

- [ ] **Step 7: Commit**

```bash
git add xtx-core/src/main/java/com/leejie/xtx/core/dto/DictItemVO.java \
        xtx-api/src/main/java/com/leejie/xtx/api/controller/DictController.java \
        xtx-api/src/test/java/com/leejie/xtx/api/controller/DictControllerTest.java
git commit -m "feat(dict): 新增 GET /dict/{typeCode} 读接口"
```

---

### Task 5: `record.category` → `record.label`

**Files:**
- Modify: `docs/sql/init.sql`（`record` 表的 `category` 列）
- Modify: `xtx-core/src/main/java/com/leejie/xtx/core/entity/Record.java`
- Modify: `xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordCreateReq.java`
- Modify: `xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordUpdateReq.java`
- Modify: `xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordVO.java`
- Test: `xtx-core/src/test/java/com/leejie/xtx/core/entity/RecordFieldStrategyTest.java`

**Interfaces:**
- Produces: `Record.label`（`String`，可空，NULL=未分类），带 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`。

- [ ] **Step 1: 写失败测试（钉住更新策略）**

创建 `RecordFieldStrategyTest.java`：

```java
package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉住 Record.label 的更新策略：NULL=未分类，必须"始终参与更新"（ALWAYS），
 * 否则用户把标签改回未分类时 label=null 会被默认 NOT_NULL 策略静默跳过、旧标签删不掉。
 *
 * <p>images 相反：null 表示"未提交该字段"（见 RecordServiceImpl 注释），必须保持默认策略。
 */
class RecordFieldStrategyTest {

    private static TableField tableField(String name) throws NoSuchFieldException {
        Field f = Record.class.getDeclaredField(name);
        return f.getAnnotation(TableField.class);
    }

    @Test
    @DisplayName("label 使用 ALWAYS，保证 label=null 能真正写库")
    void labelUsesAlwaysStrategy() throws Exception {
        TableField tf = tableField("label");
        assertNotNull(tf, "label 必须带 @TableField(updateStrategy = ALWAYS)");
        assertEquals(FieldStrategy.ALWAYS, tf.updateStrategy());
    }

    @Test
    @DisplayName("images 不得使用 ALWAYS（null 表示未提交该字段）")
    void imagesKeepsDefaultStrategy() throws Exception {
        TableField tf = tableField("images");
        assertNotNull(tf);
        assertEquals(FieldStrategy.DEFAULT, tf.updateStrategy());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=RecordFieldStrategyTest`
Expected: 编译失败 —— `Record` 无 `label` 字段（仍是 `category`）。

- [ ] **Step 3: 改 init.sql 的 record 表**

把 `docs/sql/init.sql` 中 `record` 表的这一行：

```sql
    `category`    VARCHAR(16)  NOT NULL                 COMMENT '分类:LIFE/STUDY',
```

改为：

```sql
    `label`       VARCHAR(64)  DEFAULT NULL             COMMENT '标签:dict_item.item_key(空=未分类)',
```

- [ ] **Step 4: 改 Record 实体**

`Record.java`：把

```java
    /** 分类:LIFE/STUDY */
    @Schema(description = "分类:LIFE/STUDY")
    private String category;
```

替换为（新增 `FieldStrategy` import：`import com.baomidou.mybatisplus.annotation.FieldStrategy;`）：

```java
    /**
     * 标签:dict_item.item_key(空=未分类)。
     *
     * <p>ALWAYS 策略是必需的：NULL 表示"未分类"，而默认 NOT_NULL 更新策略会跳过 null，
     * 导致改回未分类时旧标签删不掉。images 不能用这个策略（null=未提交，见 RecordServiceImpl）。
     */
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String label;
```

- [ ] **Step 5: 改三个 DTO**

`RecordCreateReq.java`：把

```java
    @Schema(description = "分类:LIFE/STUDY")
    @NotBlank(message = "分类:LIFE/STUDY不能为空")
    private String category;
```

替换为（去掉 `@NotBlank`，可空）：

```java
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
```

`RecordUpdateReq.java`：把

```java
    @Schema(description = "分类:LIFE/STUDY")
    @NotNull(message = "分类:LIFE/STUDY不能为空")
    private String category;
```

替换为：

```java
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
```

`RecordVO.java`：把

```java
    @Schema(description = "分类:LIFE/STUDY")
    private String category;
```

替换为：

```java
    @Schema(description = "标签:dict_item.item_key(空=未分类)")
    private String label;
```

（`RecordVO.fromEntity` 用 `BeanUtils.copyProperties`，字段同名 `label` 自动映射，无需改。）

- [ ] **Step 6: 运行测试确认通过**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -pl xtx-core test -Dtest=RecordFieldStrategyTest`
Expected: PASS（2 个用例）。

- [ ] **Step 7: 全模块编译，确认无残留 category 引用**

Run: `JAVA_HOME="D:\Program Files\Java\jdk-21.0.11" mvn -DskipTests install`
Expected: BUILD SUCCESS（`record` 的 `category` 引用已全部改名；`report.category` 保持不动，见 spec §8）。

- [ ] **Step 8: Commit**

```bash
git add docs/sql/init.sql \
        xtx-core/src/main/java/com/leejie/xtx/core/entity/Record.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordCreateReq.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordUpdateReq.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordVO.java \
        xtx-core/src/test/java/com/leejie/xtx/core/entity/RecordFieldStrategyTest.java
git commit -m "refactor(record): category 改为 label(可空,关联字典项)"
```

---

### Task 6: 同步项目约定文档

**Files:**
- Modify: `.claude/rules/database.md`（在「6. 字段设计决策」下追加 6.2）
- Modify: `CLAUDE.md`（在「核心架构」区补一句字典说明）

**Interfaces:**
- 无代码接口；产出为文档。

- [ ] **Step 1: 追加 database.md 约定**

在 `.claude/rules/database.md` 的「## 6. 字段设计决策」小节内，`### 6.1` 之后追加：

```markdown
### 6.2 通用字典（dict_type / dict_item）是系统级共享数据，不继承 OwnedEntity

通用字典服务全项目（首个消费者是笔记标签 `note_label`），不属于任何用户，因此两表**无 `user_id`、无 `deleted`**，不继承 `OwnedEntity` / `OwnedService`，也没有越权问题。

- 删除语义由 `enabled` 开关承担（只禁用、不硬删），故不需要逻辑删除列。
- `dict_item.dict_type` 存 `dict_type.type_code` 字符串（不是数字 id）：查某本字典的项不用 join；代价是 `type_code` 被引用后不可改名。
- 类型专属属性（如标签颜色）存 `dict_item.extra`（JSON），不占通用列——避免"为一种类型污染通用表"。
- 读接口 `GET /api/dict/{typeCode}` 只读；增 / 改 / 启停用 SQL 手动维护。
```

- [ ] **Step 2: 在 CLAUDE.md 补一句**

在 `CLAUDE.md` 的「## 核心架构：所有权隔离（OwnedService）」小节末尾追加一段：

```markdown
**例外：通用字典（`dict_type` / `dict_item`）是系统级共享数据，不继承 `OwnedEntity`**——无 `user_id`、无 `deleted`，删除语义由 `enabled` 承担。读接口 `GET /api/dict/{typeCode}`（xtx-api 的 `DictController` → xtx-core 的 `DictService`）；`record.label` 存 `dict_item.item_key`（NULL=未分类）。详见 `.claude/rules/database.md` 6.2 与 `docs/superpowers/specs/2026-10-04-generic-dict-design.md`。
```

- [ ] **Step 3: Commit**

```bash
git add .claude/rules/database.md CLAUDE.md
git commit -m "docs(dict): 补充通用字典的系统级数据约定"
```

---

## 完成标准

- `dict_type` / `dict_item` 两表 + `note_label` seed 落地；init.sql 可重复执行。
- `GET /api/dict/note_label` 返回 4 个启用项（含 `extra.color`）；未知/停用类型返回 `[]`；停用项被过滤；按 `sort_order` 升序。
- `record.label` 取代 `record.category`；`label=null` 能真正写库（改回未分类）。
- `.claude/rules/database.md` 与 `CLAUDE.md` 记录字典的系统级数据约定。
- 前端契约：删掉写死的 `LABELS`/`COLORS`，改调 `GET /api/dict/note_label`，颜色取 `extra.color`，NULL 渲染为"未分类"（前端改动在本仓库外，本计划不实现）。
