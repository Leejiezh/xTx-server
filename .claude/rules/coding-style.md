# 项目代码风格规范

本文件定义 xTx-server 项目的代码风格要求，编写或修改代码时**必须遵守**。

## 1. 注释风格

### 1.1 只写 WHY，不写 WHAT

- 方法或代码上的注释只写 **WHY**（非显然约束、隐藏意图、踩坑提示），不写 **WHAT**（行为流水账）。
- 删掉编号式注释（`// 1. 查询...`、`// 2. 校验...`、`// 3. 删除...`），除非某一步存在隐藏约束。
- 写注释前先问："删掉它，未来读者会困惑吗？" 不会 → 不写。

### 1.2 需要保留的注释类型

- 业务规则约束（例如"不属于当前用户的 id 一律按不存在处理，不返回 403"）
- workaround（绕过某个 bug 或框架限制的写法及其原因）
- 非显然分支的原因
- 跨表一致性前提（例如"record / report 两表 user_id 列名一致，用字符串列名是安全的"）

## 2. Java 21 新特性优先

本项目 JDK 为 Java 21。**有新特性就用新特性**，不要继续使用旧版本 Java 的过时写法。

### 2.1 集合首尾元素

- `list.getFirst()` / `list.getLast()` / `deque.addFirst()` / `removeLast()` 等
- 不要再写 `list.get(0)` / `list.get(list.size() - 1)`

### 2.2 switch 表达式

- 用箭头形式 `case X -> ...`，支持 `yield`、模式匹配 `case Foo f -> ...`
- 不要再写 fall-through 的传统 `switch` 语句

### 2.3 模式匹配

- `if (obj instanceof Foo f) { ... }` 直接绑定变量
- 不要再先 `instanceof` 再强转

### 2.4 Records

- 纯数据载体优先用 `record`
- 不要再写一堆 getter/setter + equals/hashCode（除非框架强制要求 JavaBean，例如 MyBatis-Plus 实体）

### 2.5 文本块

- 多行字符串用 `"""..."""`（如 SQL、JSON 模板）
- 不要再用一堆 `+ "\n" +` 拼接

### 2.6 var

- 局部变量类型明显时可用 `var` 提升可读性
- 公共 API、返回值、字段不要用 `var`

### 2.7 不可变集合

- `List.of(...)` / `Map.of(...)` / `Set.of(...)` 代替 `Collections.unmodifiableXxx(new ArrayList<>(...))` 之类的旧写法

### 2.8 Optional

- 仅作为返回值/查询结果的"存在与否"标记
- 不要用作字段或方法参数
- 链式调用 `map` / `orElse` / `orElseThrow` 替代显式 null 判断

### 2.9 Stream

- `toList()`（Java 16+）替代 `collect(Collectors.toList())`
- `mapMulti`、`takeWhile`、`dropWhile` 等按需使用

### 2.10 Sequenced Collections（Java 21）

- 需要顺序访问首尾的场景优先用 `SequencedCollection` 接口方法

### 2.11 旧代码处理

- 遇到旧代码使用过时写法，**在本次任务范围内**可顺手改造
- 超出任务范围的不主动改，避免污染 diff

## 3. 工具类选型优先级

优先级链：**JDK 原生 > Spring > Hutool**。前者能一两行写完的，不引入后者。

### 3.1 三级链的理由

- **JDK 原生**：零依赖，承接第 2 节「Java 21 新特性优先」
- **Spring**：Spring Boot 已在 classpath，无额外依赖成本，语义与框架一致
- **Hutool**：仅当前两者没有等价能力、或写法明显更繁琐时使用

### 3.2 高频场景对照

| 场景 | 首选 | 备注 |
|---|---|---|
| 字符串判空 | `s == null \|\| s.isBlank()`（JDK） | 需要「非空且有内容」用 Spring `StringUtils.hasText`，注意它是**正向**语义，别和 `isBlank` 写反 |
| 集合判空 | `CollectionUtils.isEmpty`（Spring `org.springframework.util`） | 不用 Hutool `CollUtil` |
| 不可变集合 | `List.of` / `Map.of`（JDK） | 见 2.7 |
| 空值断言 | `Objects.requireNonNull`（JDK） | **业务校验一律抛 `BusinessException`**，不用 Spring `Assert` |
| Bean 拷贝 | `BeanUtils.copyProperties`（Spring） | 不用 Hutool `BeanUtil` |
| 日期时间 | `java.time`（JDK） | **禁止** Hutool `DateUtil` 与 `java.util.Date` |
| JSON | Jackson `ObjectMapper`（Spring Boot 自带） | 不用 Hutool `JSONUtil`，避免两套序列化规则与接口出参不一致 |
| 流/文件拷贝 | `StreamUtils` / `FileCopyUtils`（Spring） | MinIO 场景可用 Hutool `IoUtil` |
| 摘要/加密 | Hutool `DigestUtil` / `SecureUtil` | Spring 无等价实现 |
| 随机 ID | `UUID.randomUUID()`（JDK）或 Hutool `IdUtil` | 主键由 MySQL 自增，此项仅用于文件名等场景 |

### 3.3 不引入其他工具库

工具类来源只有 JDK + Spring + Hutool 三处。**不要引入** commons-lang3、Guava、commons-collections——多个同名 `StringUtils` 混用是长期维护负担。
