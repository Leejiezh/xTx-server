# 代码风格与命名

## 命名
- 类、方法、变量命名遵循阿里巴巴 Java 开发手册，方法名以动词开头。
- Controller、Service、Mapper 三层的方法名尽量保持一致，例如 `pageEmployee` → `pageEmployee` → `pageEmployee`。确实无法一致时（一个 Service 方法被多个接口复用，或 Mapper 方法需体现 SQL 细节），以语义清晰为准。
- 私有辅助方法名要说明意图，不要用 `process`、`handle`、`doSomething` 这类含糊的名字。

## 方法动词约定
| 操作 | 前缀 | 示例 |
|---|---|---|
| 查单个 | `get` | `getEmployeeById` |
| 查列表 | `list` | `listEmployeeByDeptId` |
| 分页查询 | `page` | `pageEmployee` |
| 统计数量 | `count` | `countEmployeeByStatus` |
| 判断是否存在 | `exists` | `existsByIdCard` |
| 布尔判断 | `is` / `has` | `isActive`、`hasPermission` |
| 新增 | `save` | `saveEmployee` |
| 修改 | `update` | `updateEmployee` |
| 删除 | `remove` | `removeEmployee` |
| 批量操作 | `batch` + 动词 | `batchSaveEmployee` |
| 导入导出 | `import` / `export` | `exportEmployee` |
| 转换 | `convert` / `to` | `convertToVO` |
| 校验 | `check` / `validate` | `checkEmployeeUnique` |

- 同一种操作全项目只用一个动词，不混用 `get / query / find / select`，也不混用 `save / add / insert / create`。
- 按条件查询用 `By` 连接。
- 布尔方法返回 `boolean`，名称读起来是个问句；不要写成 `checkXxx` 却返回布尔。
- Mapper 自定义方法不要和 `BaseMapper` 自带的 `selectById`、`insert`、`updateById`、`deleteById` 同名。

## 业务层结构
- 写方法前先想一下能否做成公共方法。与主业务无关、或能被其他业务复用的逻辑，单独提取。
- 一个方法不要写太长（参考约 50 行，嵌套不超过 3 层），能拆就拆，主方法读起来像流程大纲。
- 公共方法按复用范围放置：本类私有方法 → 公共 Manager → `utils` 工具类。

## 简洁与注释
- 代码保持简洁，逻辑块之间空一行，不要多余空行和冗余代码。
- 该写注释的地方写，但要简短、通俗易懂，说"为什么"，不复述代码在做什么。
- 公共方法和类写 Javadoc，保持简短。
- 禁止未使用的 import、变量和注释掉的废弃代码。

## 其他
- 优先使用 JDK 17 特性（`record`、`switch` 表达式、文本块、`instanceof` 模式匹配），DTO 能用 `record` 就用 `record`。
- 使用 Lombok 减少样板代码，但实体类避免滥用 `@Data` 导致 `equals/hashCode` 问题。
- 新增依赖前先检查是否已有可替代的，尤其是 Hutool 已覆盖的功能。
