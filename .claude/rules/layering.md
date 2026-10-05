# 分层、返回与校验

## 分层
- Controller 只做参数接收、校验和结果封装，不写业务逻辑。
- Service 负责业务编排，不直接拼 SQL，不处理 HTTP 对象（`HttpServletRequest` 等）。
- 禁止跨层调用，例如 Controller 直接调用 Mapper。
- 对象分离：`DTO`（入参）、`VO`（出参）、`Entity`（持久化）、`BO`（业务内部）；禁止 Entity 直接返回给前端。
- 依赖注入使用构造器注入（`@RequiredArgsConstructor`），不使用字段 `@Autowired`。

## 参数校验
- Controller 层入参使用 `@Valid` / `@Validated` + 注解校验（`@NotBlank`、`@NotNull`、`@Size` 等），不在业务层用 if 判断参数。
- 注解满足不了的复杂校验，使用自定义校验注解，仍不放进业务方法里。

## 统一返回与异常
- 所有接口统一返回 `Result<T>`，不直接返回裸对象。
- 业务异常统一抛自定义 `BusinessException`，错误码来自错误码枚举。
- 全局异常由 `@RestControllerAdvice` 统一处理，业务代码不要 `try-catch` 后吞掉异常。
- `catch` 后必须记录日志或重新抛出，禁止空 `catch`。
