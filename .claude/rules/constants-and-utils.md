# 工具类、常量与枚举

## 工具类
- 优先使用 Hutool（`StrUtil`、`CollUtil`、`MapUtil`、`BeanUtil`、`DateUtil`、`IdUtil`、`JSONUtil` 等），不重复造轮子，也不混用 Apache Commons / Guava 的同类方法。
- 字符串判空用 `StrUtil.isBlank/isNotBlank`，集合判空用 `CollUtil.isEmpty/isNotEmpty`。
- 对象拷贝用 `BeanUtil.copyProperties`，禁止手写大段 getter/setter 赋值。
- 项目内通用工具方法放在 `utils` 包，类名以 `Util` 结尾，私有构造器，只放静态方法。

## 常量与枚举
- 业务状态、类型、编码等固定取值用枚举，提供 `code`、`desc` 和 `getByCode` 方法。
- 非枚举常量统一放 `constants` 包下的常量类（如 `RedisKeyConstants`、`CommonConstants`），常量类 `final` + 私有构造器。
- 禁止在 Service / Controller / Manager 等业务类中定义 `static final` 常量。
- 禁止魔法值（数字、字符串直接写在逻辑里），包括状态值、Redis key、请求头名称、错误信息。
- 枚举与数据库字段映射时，使用 MyBatis-Plus 的 `@EnumValue` 或统一的类型处理器。
