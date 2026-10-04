# 通用字典（Dictionary）设计文档

日期：2026-10-04
状态：待批准

## 1. 背景与目标

**现状**：`record.category` 用写死的 `LIFE` / `STUDY` 两个值（见 `docs/sql/init.sql`）；前端把 `work` / `design` / `tech` / `life` 这套标签及其展示名、颜色写死在 `LABELS` / `COLORS` 常量里。

**目标**：把这类"枚举"从代码写死改为**数据驱动的通用字典**，服务整个项目（不止笔记标签）。首个消费者是笔记标签。支持用 SQL 增删改标签、启停标签，无需改代码、无需重新部署。

### 1.1 已确认的范围决策

| 决策 | 结论 |
|---|---|
| 字典归属 | **全局一套**（系统级，无 `user_id`），不继承 `OwnedEntity` |
| 与 `record.category` 关系 | **替换**：`record.category` → `record.label` |
| 维护方式 | **只做读接口**；增 / 改 / 启停用 SQL 手动维护 |
| 一条笔记的标签数 | 只能一个 |
| 未分类 | `record.label` 允许 **NULL**，前端把 NULL 渲染为"未分类" |
| 表结构 | **两表**：`dict_type`（类型目录）+ `dict_item`（字典项） |
| `dict_type` 存储 | 存**字符串码**，不用 Java 枚举作存储类型 |
| 颜色等类型专属属性 | 存 `dict_item.extra`（JSON），不占通用列 |

### 1.2 不在本次范围

- 管理后台 / 管理接口（本次只有读接口，写操作走 SQL）。
- `report.category` 的改造（见第 8 节：报告生成尚未实现，且 NULL 语义与 record 不同，留待报告功能落地时一并定）。
- 前端实现（本仓库是后端；第 6 节只给接口契约）。

## 2. 表结构

两张表都不继承 `OwnedEntity`（无 `user_id`、无 `deleted`），而是继承 `BaseEntity`（`id` 雪花主键 / `created_at` / `updated_at`）。删除语义由 `enabled` 开关承担，不需要逻辑删除列。

```sql
-- ========================================
-- 5. 字典类型表（类型目录：全项目有哪些字典）
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

### 2.1 设计要点

- **通用列 + 扩展列分离**：`dict_type` / `item_key` / `item_label` / `sort_order` / `enabled` / `remark` 是所有字典类型都成立的列；类型专属属性（如标签的颜色）走 `extra` JSON，别的类型不用就为空。将来某本字典要 `icon` / `badge` / `level`，塞 `extra` 即可，**不改表结构**。
- **`dict_item.dict_type` 存 `type_code` 字符串，不是 `dict_type.id` 数字**：查某本字典的项时不用 join，直接用 code 查 item。代价是 `type_code` 一旦有 item 引用就**不可改名**（约定，靠 seed 与 SQL 维护时自觉遵守）。
- **两表关系**：`dict_type` 是"有哪些字典"的显式目录（可描述、可整本启停、可先建类型后加项）；`dict_item` 是字典内容。单表方案做不到这些，故采用两表。
- **不建外键**：与现有 `init.sql` 一致（record/report 也未建 FK），完整性靠约定与 seed 维护。

### 2.2 初始数据（seed）

`note_label` 是首个字典类型。**"未分类"不是字典项**（它是 `record.label` 为 NULL 的状态），故 seed 只有 4 项。前端原 `all: '未分类'` 这一键被丢弃。

```sql
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

用 `ON DUPLICATE KEY UPDATE` 而非裸 `INSERT`，保证 init.sql 可重复执行（本地库靠重建对齐，见 `.claude/rules/database.md` 第 4 节）。

## 3. 实体与分层

沿用项目既有分层：实体 / Mapper / Service 在 `xtx-core`，Controller 在 `xtx-api`（与 `RecordController` / `RecordService` 的分布一致）。

```
xtx-core/src/main/java/com/leejie/xtx/core/
├── entity/DictType.java          ← extends BaseEntity
├── entity/DictItem.java          ← extends BaseEntity，extra 用 JSON TypeHandler
├── mapper/DictTypeMapper.java    ← extends BaseMapper<DictType>
├── mapper/DictItemMapper.java    ← extends BaseMapper<DictItem>
├── dto/DictItemVO.java           ← { key, label, sortOrder, extra }
├── service/DictService.java      ← 接口（不是 OwnedService）
└── service/impl/DictServiceImpl.java

xtx-api/src/main/java/com/leejie/xtx/api/controller/
└── DictController.java           ← GET /dict/{typeCode}
```

### 3.1 实体

- `DictType`：字段与 `dict_type` 列一一对应；`@TableName("dict_type")`；Lombok `@Data` + `@EqualsAndHashCode(callSuper = true)`。
- `DictItem`：字段与 `dict_item` 列一一对应；`extra` 用 `Map<String, Object>`，配 MyBatis-Plus 的 `JacksonTypeHandler`：
  ```java
  @TableName(value = "dict_item", autoResultMap = true)
  ...
  @TableField(typeHandler = JacksonTypeHandler.class)
  private Map<String, Object> extra;
  ```
  `autoResultMap = true` 是自定义 TypeHandler 生效的前提（与 `Record.images` 同理，见 `Record.java` 注释）。

### 3.2 为什么不是 OwnedService

字典是**系统级共享数据**，不属于任何用户，因此不继承 `OwnedService` / `OwnedEntity`，也没有越权（IDOR）问题——任何已登录用户都可以读到全部字典项。**不要**给这两张表加 `user_id`，那会引入不必要的用户隔离与缓存复杂度。

### 3.3 DictService

```java
public interface DictService {
    /** 取某本字典的启用项，按 sort_order 升序；类型不存在或整本停用时返回空列表 */
    List<DictItem> listEnabledItems(String typeCode);
}
```

`DictServiceImpl` 注入 `DictTypeMapper` + `DictItemMapper`：先查 `dict_type`（不存在或 `enabled=0` → 返回空列表），再查 `dict_item`（`dict_type = typeCode AND enabled = 1`，`ORDER BY sort_order ASC, id ASC`）。

MVP 不做缓存：字典表极小、读频率可控，直接查库即可。将来需要时用项目已有的 Redis 加缓存（失效点是 SQL 改数据后手动清 key）。

## 4. 读接口

```
GET /api/dict/{typeCode}
```

- 出参：`R<List<DictItemVO>>`
- `DictItemVO` 字段：`key`（= `item_key`）、`label`（= `item_label`）、`sortOrder`、`extra`（JSON 对象原样透传，如 `{"color":"#7C3AED"}`）
- 只返回 `enabled = 1` 的项，按 `sort_order` 升序；`typeCode` 不存在或整本停用时返回空数组 `[]`。
- 认证：**默认需要登录**（不在 `SecurityConfig.PUBLIC_PATHS` 白名单内）。字典只是展示元数据，登录后拉取即可，无需免认证。
- `extra` **原样透传**而非把 `color` 拍平到顶层——拍平是标签专属逻辑，会破坏"通用字典"的接口形态。前端自行读 `item.extra.color`。

示例响应：

```json
{
  "code": 200,
  "msg": "ok",
  "data": [
    { "key": "work",   "label": "工作", "sortOrder": 10, "extra": { "color": "#7C3AED" } },
    { "key": "design", "label": "设计", "sortOrder": 20, "extra": { "color": "#EC4899" } }
  ]
}
```

## 5. record 改造

`record.category`（`VARCHAR(16) NOT NULL`）→ `record.label`（`VARCHAR(64) DEFAULT NULL`），存 `dict_item.item_key`（稳定字符串 key，**不用数字 id**，改文案不破坏引用）。NULL 表示未分类。

改动清单：

| 文件 | 改动 |
|---|---|
| `docs/sql/init.sql` | `record` 表：`category` → `label`，`NOT NULL` → `DEFAULT NULL`，同步列注释 |
| `xtx-core/entity/Record.java` | 字段 `category` → `label`，`@Schema` 描述改为"标签:dict_item.item_key(空=未分类)" |
| `xtx-core/dto/RecordCreateReq.java` | `category` → `label`，**去掉** `@NotBlank`（可空） |
| `xtx-core/dto/RecordUpdateReq.java` | `category` → `label`，**去掉** `@NotNull`（可空） |
| `xtx-core/dto/RecordVO.java` | `category` → `label` |

说明：`RecordController.page` 目前传的是 `null` 筛选（分类筛选尚未接线），故本次**不需要**改筛选逻辑。若前端需要按标签筛选，另开一次改动在 `page` 里补 `wrapper.eq("label", ...)`。

### 5.1 清空标签（改回"未分类"）的处理

`OwnedServiceImpl.update` 走的是 `super.updateById(entity)`，MyBatis-Plus 默认 `NOT_NULL` 策略会**跳过 null 字段**。而"未分类"恰好是 `label = NULL` —— 于是用户把某条记录的标签从 `work` 改回"未分类"时，前端传 `label = null` 会被静默跳过，旧标签删不掉。这是 `NULL = 未分类` 这个选择带来的直接后果，必须在字段上处理：

- `Record.label` 加 `@TableField(updateStrategy = FieldStrategy.ALWAYS)`，让它**始终参与更新**（即使值为 null）。`RecordUpdateReq` 是全量更新 DTO（每次携带完整记录），因此"始终写 label"语义正确。
- 该设置只作用于 `label` 一个字段；`OwnedServiceImpl.update` 里 `setUserId(null)` / `setDeleted(null)` 仍靠默认 `NOT_NULL` 策略跳过，不受影响。
- 对照 `images`：`images` 用的是默认策略（null = 未提交该字段），语义与 `label` 相反，**不要**给 `images` 加 ALWAYS。（`RecordServiceImpl` 的注释已说明 images 的 null 语义。）
- 落地时确认 `FieldStrategy` 的枚举名与 MyBatis-Plus 3.5.9 一致（`updateStrategy` 属性 + `FieldStrategy.ALWAYS`）。

## 6. 前端契约（本仓库不实现，仅约定）

- 删除写死的 `LABELS` / `COLORS` 常量，改为启动后调 `GET /api/dict/note_label` 拉取（带 JWT）。
- 展示名取 `item.label`，颜色取 `item.extra?.color`；`item.extra` 缺 `color` 时用兜底色。
- 记录 `label` 为 `null` 时渲染为"未分类"。
- 标签选择器只列接口返回的项（已过滤停用项）。

## 7. 测试

- `DictService`：
  - 正常类型返回其启用项且按 `sort_order` 升序；
  - 停用项（`enabled=0`）不返回；
  - 类型不存在 → 空列表；
  - 类型整本停用（`dict_type.enabled=0`）→ 空列表。
- `DictItem.extra` 的 JSON 往返：写入 `Map` → 读出相等（验证 `JacksonTypeHandler` 生效）。
- record 字段重命名后，既有 record 的增删改查测试需同步改字段名。
- **清空标签**：把一条 `label = 'work'` 的记录更新为 `label = null`，读回应为 `null`（验证 5.1 的 `updateStrategy = ALWAYS` 生效）。

测试库需先执行 `docs/sql/init.sql`（含 seed）。（按项目规则，是否运行测试由用户决定。）

## 8. 后续 / 已知取舍

- **`report.category` 未纳入本次**：报告生成功能尚未实现，且"未分类"在 record 里是 NULL，而报告的 `ALL` 表示"不按标签筛选"，两者 NULL 语义不同。等报告功能落地时统一设计（可能是 `report.label` + 约定 NULL=全部）。
- **`type_code` 不可改名**：一旦有 item 引用，改名会断引用。当前靠约定；将来若要做管理端，需加校验。
- **无缓存**：MVP 直接查库；量级上来再加 Redis。
- **同步更新 `.claude/rules/database.md`**：追加一条约定——字典两表是系统级数据、不继承 `OwnedEntity`、无 `deleted`，删除语义由 `enabled` 承担。

## 9. 落地文件清单

新增：
- `docs/sql/init.sql` 中的 `dict_type` / `dict_item` 建表与 seed
- `xtx-core/.../entity/DictType.java`、`DictItem.java`
- `xtx-core/.../mapper/DictTypeMapper.java`、`DictItemMapper.java`
- `xtx-core/.../dto/DictItemVO.java`
- `xtx-core/.../service/DictService.java`、`service/impl/DictServiceImpl.java`
- `xtx-api/.../controller/DictController.java`
- 测试文件

修改：
- `docs/sql/init.sql`：`record` 表 `category` → `label`
- `xtx-core/.../entity/Record.java` 及 `dto/RecordCreateReq.java`、`RecordUpdateReq.java`、`RecordVO.java`
- `.claude/rules/database.md`（追加字典约定）
