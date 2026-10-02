# 微信小程序登录 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通「小程序 `wx.login()` 拿 code → 后端换 openid → 查/建 user → 签发 JWT → 返回 token + 精简用户信息」这条链路，让小程序端首次可真正登录。

**Architecture:** 沿用现有三层分工与依赖方向 `xtx-api → xtx-core → xtx-wechat → xtx-common`。`xtx-wechat` 用已声明但未使用的 WxJava SDK（`weixin-java-miniapp` 4.8.3.B）装配 `WxMaService` 并封装 jscode2session，对外只暴露项目自己的 `WxLoginResult`，不把 SDK 类型漏进上层；`xtx-core` 的 `AuthServiceImpl` 组合「微信换 openid」+「user 表查/建」+「JWT 签发」；`xtx-api` 只放一个 `POST /auth/login` 控制器。认证链路（`JwtAuthFilter` / `SecurityUtils` / `SecurityConfig` 的 `/auth/**` 放行）已存在，本计划不动。

**Tech Stack:** Java 21、Spring Boot 3.4、MyBatis-Plus 3.5.9、jjwt 0.12.6、WxJava（weixin-java-miniapp）4.8.3.B、JUnit 5 + Mockito、springdoc-openapi。

**Spec:** `docs/superpowers/specs/2026-08-05-ai-record-assistant-design.md`（见第 4 节「流程 1:微信登录」时序图与第 5 节 API 约定）

## Global Constraints

- **Java 21**：纯数据载体优先 `record`（`WxLoginResult` / `LoginReq` / `LoginVO`），局部变量可用 `var`，不写旧式 `switch`。
- **模块依赖方向不可逆**：`xtx-api → xtx-core → xtx-wechat → xtx-common`。WxJava 类型（`WxMaService` 等）只允许出现在 `xtx-wechat`。
- **统一响应** `R<T>`（`R.ok(data)` / `R.fail(code, msg)`）；**业务异常一律抛 `BusinessException(code, msg)`**，由 `GlobalExceptionHandler` 转成 `R`。
- **工具类优先级** JDK 原生 > Spring > Hutool（本计划只需 Spring `BeanUtils.copyProperties`）。
- **注释只写 WHY**：不写「1. 查询 2. 校验」这类流水账。
- **测试风格**：JUnit 5 + Mockito，`@ExtendWith(MockitoExtension.class)` + `@Mock` + `@DisplayName`，在 `@BeforeEach` 里手动 `new` 被测对象（照抄 `xtx-core/.../FileServiceImplPresignTest.java`）。
- **构建环境**：系统 `JAVA_HOME` 指向 JDK 17，**每条 mvn 命令都必须显式覆盖** `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11"`，否则报「不受支持的发行版本 21」。
- **按项目规则，本计划不主动执行最终编译/打包/启动/curl/SQL 人工验证**；各任务内的单测命令属于任务自身的 TDD 循环，应当执行。
- **提交信息**：中文 + 前缀，如 `feat(auth): 落地微信小程序登录`。
- **本次不落库 `session_key`**（用户决策）：`AuthServiceImpl` 只消费 `openid`。
- **首登自动建用户**（用户决策）：`user.nickname` / `avatar_url` 留空，由前端后续引导补充。
- **返回精简 VO**（用户决策）：`UserVO` 只含 `id` / `nickname` / `avatarUrl`。

## Review Focus

以下 5 类输入/条件最容易让使用者踩坑，其中可自动化的一律在对应任务里用测试钉死：

1. **`code` 失效或已被使用（微信 errcode 40029 等）** —— 使用者期望得到「登录失败，请重试」而不是 500。由 Task 1 的 `login_translatesWxError` 钉死。
2. **同一 openid 并发首登（双击登录按钮）** —— 期望两次都成功、拿到同一个 userId，而不是第二次 500。由 Task 2 的 `login_concurrentFirstLogin_fallsBackToExistingRow` 钉死。
3. **token 里的 userId 与数据库那行必须一致** —— 期望登录后立刻能用 token 读到自己的数据，不能签成别人的 id。由 Task 2 的 `login_existingUser_reusesRow` 解析 JWT 断言钉死。
4. **登录端点必须免认证可达** —— 期望不带头部 `Authorization` 也能调通 `/auth/login`。依赖 `SecurityConfig.PUBLIC_PATHS` 里已存在的 `/auth/**`；**无自动化测试**，Task 3 给出手工验证步骤。
5. **`wechat.app-id` / `wechat.app-secret` 缺失或仍是占位值时** —— 期望失败信息能指向配置，而不是静默签出一个无法使用的登录态。**无自动化测试**，Task 3 给出手工验证步骤。

---

### Task 1: 装配 WxJava 客户端并实现 WechatLoginService（xtx-wechat）

**Files:**
- Modify: `xtx-wechat/src/main/java/com/leejie/xtx/wechat/config/WechatConfig.java`
- Create: `xtx-wechat/src/main/java/com/leejie/xtx/wechat/config/WxMaServiceConfig.java`
- Modify: `xtx-wechat/src/main/java/com/leejie/xtx/wechat/dto/WxLoginResult.java`
- Create: `xtx-wechat/src/main/java/com/leejie/xtx/wechat/service/impl/WechatLoginServiceImpl.java`
- Test: `xtx-wechat/src/test/java/com/leejie/xtx/wechat/service/impl/WechatLoginServiceImplTest.java`

**Interfaces:**
- Consumes: `com.leejie.xtx.common.exception.BusinessException(int code, String message)`
- Produces:
  - `com.leejie.xtx.wechat.dto.WxLoginResult` — `record WxLoginResult(String openid, String sessionKey, String unionid)`
  - `com.leejie.xtx.wechat.service.WechatLoginService.login(String code)` → `WxLoginResult`（接口已存在，签名不变）
  - Spring Bean `cn.binarywang.wx.miniapp.api.WxMaService`（`WxMaServiceConfig` 提供）

- [ ] **Step 1: 精简 `WechatConfig`，删掉随 WxJava 接入而失效的字段**

`loginUrl` / `grantType` 是原先手写 HTTP 方案留下的，改用 WxJava 后不再有人读，留着会误导后来者以为接口是手拼的。`application.yml` 只配置了 `wechat.app-id` / `wechat.app-secret`，删除这两个字段不影响绑定。

```java
package com.leejie.xtx.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "wechat")
public class WechatConfig {

    private String appId;
    private String appSecret;
}
```

- [ ] **Step 2: 新增 `WxMaServiceConfig` 装配 `WxMaService`**

模块只依赖 `weixin-java-miniapp`（core），**没有**引入 `wx-java-miniapp-spring-boot-starter`，因此必须手工建 Bean，不能指望自动配置。

```java
package com.leejie.xtx.wechat.config;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.impl.WxMaServiceImpl;
import cn.binarywang.wx.miniapp.config.impl.WxMaDefaultConfigImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * WxJava 小程序客户端装配。
 *
 * <p>只依赖 weixin-java-miniapp（core），没有引入 wx-java-miniapp-spring-boot-starter，
 * 所以这里手工建 WxMaService 单例，而非依赖 starter 自动配置。
 */
@Configuration
@RequiredArgsConstructor
public class WxMaServiceConfig {

    private final WechatConfig wechatConfig;

    @Bean
    public WxMaService wxMaService() {
        WxMaDefaultConfigImpl config = new WxMaDefaultConfigImpl();
        config.setAppid(wechatConfig.getAppId());
        config.setSecret(wechatConfig.getAppSecret());

        WxMaService service = new WxMaServiceImpl();
        service.setWxMaConfig(config);
        return service;
    }
}
```

- [ ] **Step 3: 把 `WxLoginResult` 改为 record，并删掉失败字段**

微信用 `errcode`/`errmsg` 表达失败，WxJava 会把它们抛成 `WxErrorException`，因此本 DTO 只承载成功字段。`sessionKey` 保留在边界上（当前不落库），后续要做手机号解密时无需再改这个契约。

```java
package com.leejie.xtx.wechat.dto;

/**
 * 微信 jscode2session 的返回。
 *
 * <p>失败由 WxJava 抛 WxErrorException 表达，故这里只有成功字段。
 */
public record WxLoginResult(String openid, String sessionKey, String unionid) {
}
```

- [ ] **Step 4: 写失败测试 `WechatLoginServiceImplTest`**

`WxMaUserService.getSessionInfo` 声明了受检异常 `WxErrorException`，测试方法需 `throws Exception`。Mockito 的 `thenThrow` 传受检异常需要该签名配合。

```java
package com.leejie.xtx.wechat.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.api.WxMaUserService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import me.chanjar.weixin.common.error.WxError;
import me.chanjar.weixin.common.error.WxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WechatLoginServiceImplTest {

    @Mock
    private WxMaService wxMaService;
    @Mock
    private WxMaUserService wxMaUserService;

    private WechatLoginServiceImpl wechatLoginService;

    @BeforeEach
    void setUp() {
        wechatLoginService = new WechatLoginServiceImpl(wxMaService);
    }

    @Test
    @DisplayName("登录成功：透传 openid / sessionKey / unionid")
    void login_mapsSessionFields() throws Exception {
        WxMaJscode2SessionResult session = new WxMaJscode2SessionResult();
        session.setOpenid("o-abc");
        session.setSessionKey("sk-123");
        session.setUnionid("u-xyz");
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("code-1")).thenReturn(session);

        WxLoginResult result = wechatLoginService.login("code-1");

        assertEquals("o-abc", result.openid());
        assertEquals("sk-123", result.sessionKey());
        assertEquals("u-xyz", result.unionid());
    }

    @Test
    @DisplayName("微信返回 errcode：转成 400 业务异常，不让它冒成 500")
    void login_translatesWxError() throws Exception {
        when(wxMaService.getUserService()).thenReturn(wxMaUserService);
        when(wxMaUserService.getSessionInfo("bad"))
                .thenThrow(new WxErrorException(new WxError(40029, "invalid code")));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> wechatLoginService.login("bad"));

        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("invalid code"));
    }
}
```

- [ ] **Step 5: 跑测试，确认失败（编译失败即视为失败）**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-wechat -am test -Dtest=WechatLoginServiceImplTest -DfailIfNoSpecifiedTests=false`
Expected: 编译失败，`找不到符号: 类 WechatLoginServiceImpl`。

- [ ] **Step 6: 写最小实现 `WechatLoginServiceImpl`**

```java
package com.leejie.xtx.wechat.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.leejie.xtx.common.exception.BusinessException;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import com.leejie.xtx.wechat.service.WechatLoginService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WechatLoginServiceImpl implements WechatLoginService {

    private final WxMaService wxMaService;

    @Override
    public WxLoginResult login(String code) {
        try {
            WxMaJscode2SessionResult session = wxMaService.getUserService().getSessionInfo(code);
            return new WxLoginResult(session.getOpenid(), session.getSessionKey(), session.getUnionid());
        } catch (WxErrorException e) {
            // code 失效/已被使用（40029 等）属客户端问题，转 400，避免冒成 500 掩盖原因
            throw new BusinessException(400, "微信登录失败: " + e.getError().getErrorMsg());
        }
    }
}
```

- [ ] **Step 7: 跑测试，确认通过**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-wechat -am test -Dtest=WechatLoginServiceImplTest -DfailIfNoSpecifiedTests=false`
Expected: `Tests run: 2, Failures: 0, Errors: 0`，BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
git add xtx-wechat/src/main/java/com/leejie/xtx/wechat/config/WechatConfig.java \
        xtx-wechat/src/main/java/com/leejie/xtx/wechat/config/WxMaServiceConfig.java \
        xtx-wechat/src/main/java/com/leejie/xtx/wechat/dto/WxLoginResult.java \
        xtx-wechat/src/main/java/com/leejie/xtx/wechat/service/impl/WechatLoginServiceImpl.java \
        xtx-wechat/src/test/java/com/leejie/xtx/wechat/service/impl/WechatLoginServiceImplTest.java
git commit -m "feat(wechat): 接入 WxJava 实现 jscode2session"
```

---

### Task 2: user 查/建与 JWT 签发（xtx-core）

**Files:**
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/mapper/UserMapper.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/dto/LoginReq.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/dto/UserVO.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/dto/LoginVO.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/service/AuthService.java`
- Create: `xtx-core/src/main/java/com/leejie/xtx/core/service/impl/AuthServiceImpl.java`
- Test: `xtx-core/src/test/java/com/leejie/xtx/core/service/impl/AuthServiceImplTest.java`

**Interfaces:**
- Consumes: `WxLoginResult` / `WechatLoginService`（Task 1）；`JwtUtils.generate(Map<String,Object>)`、`Constants.CLAIM_USER_ID`、`Constants.CLAIM_OPENID`（既有，xtx-common）；`User` 实体（既有，含 `openid`/`nickname`/`avatarUrl`）
- Produces:
  - `com.leejie.xtx.core.mapper.UserMapper extends BaseMapper<User>`
  - `com.leejie.xtx.core.service.AuthService.login(String code)` → `LoginVO`
  - `com.leejie.xtx.core.dto.LoginVO` — `record LoginVO(String token, UserVO userInfo)`
  - `com.leejie.xtx.core.dto.UserVO` — `@Data` 类，字段 `id` / `nickname` / `avatarUrl`，静态工厂 `UserVO.fromEntity(User)`
  - `com.leejie.xtx.core.dto.LoginReq` — `record LoginReq(String code)`

- [ ] **Step 1: 新增 `UserMapper`**

`user` 表无 `user_id` / `deleted`，不继承 `OwnedEntity`，因此不能走 `OwnedService`，登录读写直接落在 Mapper 上。`@MapperScan("com.leejie.xtx.core.**.mapper")`（`MyBatisPlusConfig`）会自动扫到本包。

```java
package com.leejie.xtx.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.leejie.xtx.core.entity.User;

/**
 * 用户表 Mapper。
 *
 * <p>user 表不继承 OwnedEntity（无 user_id / deleted），不能走 OwnedService，
 * 登录相关的读写直接落在本 Mapper 上。
 */
public interface UserMapper extends BaseMapper<User> {
}
```

- [ ] **Step 2: 新增 DTO —— `LoginReq` / `UserVO` / `LoginVO`**

`UserVO` 用 `@Data` + `BeanUtils`（与 `RecordVO` 一致）；`LoginReq` / `LoginVO` 是纯数据载体，按 Java 21 约定用 `record`。

```java
package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "微信登录请求")
public record LoginReq(
        @Schema(description = "wx.login 获取的临时 code")
        @NotBlank(message = "code 不能为空")
        String code) {
}
```

```java
package com.leejie.xtx.core.dto;

import com.leejie.xtx.core.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Schema(description = "用户视图对象(精简)")
@Data
public class UserVO {

    @Schema(description = "主键")
    private Long id;
    @Schema(description = "昵称")
    private String nickname;
    @Schema(description = "头像URL")
    private String avatarUrl;

    public static UserVO fromEntity(User entity) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
```

```java
package com.leejie.xtx.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record LoginVO(
        @Schema(description = "JWT，前端存本地并在后续请求的 Authorization 头回传")
        String token,
        @Schema(description = "用户信息")
        UserVO userInfo) {
}
```

- [ ] **Step 3: 新增 `AuthService` 接口**

```java
package com.leejie.xtx.core.service;

import com.leejie.xtx.core.dto.LoginVO;

public interface AuthService {

    /**
     * 微信小程序登录：code → openid → 查/建 user → 签发 JWT。
     *
     * @param code 前端 wx.login() 获取的临时 code
     */
    LoginVO login(String code);
}
```

- [ ] **Step 4: 写失败测试 `AuthServiceImplTest`**

`selectOne(any(Wrapper.class))` 必须显式写 `Wrapper.class`，否则与 MP 的同名重载产生歧义。第三个用例里 `thenReturn(null, winner)` 模拟「首次查不到 → 插入撞唯一键 → 回读拿到赢家」。

```java
package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.wechat.dto.WxLoginResult;
import com.leejie.xtx.wechat.service.WechatLoginService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private WechatLoginService wechatLoginService;
    @Mock
    private UserMapper userMapper;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(wechatLoginService, userMapper);
    }

    @Test
    @DisplayName("老用户：复用已有行不插入，token 里的 userId 与库一致")
    void login_existingUser_reusesRow() {
        User existing = new User();
        existing.setId(7L);
        existing.setOpenid("o-1");
        existing.setNickname("兔兔");
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-1", "sk", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        LoginVO vo = authService.login("c");

        assertEquals(7L, vo.userInfo().getId());
        assertEquals("兔兔", vo.userInfo().getNickname());
        assertNotNull(vo.token());
        verify(userMapper, never()).insert(any());

        Claims claims = JwtUtils.parse(vo.token());
        assertEquals(7L, ((Number) claims.get(Constants.CLAIM_USER_ID)).longValue());
        assertEquals("o-1", claims.get(Constants.CLAIM_OPENID));
    }

    @Test
    @DisplayName("首登：插入 user 并回填自增 id，返回该 id 的 token")
    void login_newUser_createsRow() {
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-2", "sk", null));
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(42L); // 模拟 MySQL 自增主键回填
            return 1;
        });

        LoginVO vo = authService.login("c");

        assertEquals(42L, vo.userInfo().getId());

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(cap.capture());
        assertEquals("o-2", cap.getValue().getOpenid());

        Claims claims = JwtUtils.parse(vo.token());
        assertEquals(42L, ((Number) claims.get(Constants.CLAIM_USER_ID)).longValue());
    }

    @Test
    @DisplayName("并发首登撞唯一键：回读赢家行，不抛异常")
    void login_concurrentFirstLogin_fallsBackToExistingRow() {
        User winner = new User();
        winner.setId(9L);
        winner.setOpenid("o-3");
        when(wechatLoginService.login("c")).thenReturn(new WxLoginResult("o-3", "sk", null));
        // 首次查询还没有行；插入撞 uk_openid；回读时拿到赢家那行
        when(userMapper.selectOne(any(Wrapper.class))).thenReturn(null, winner);
        when(userMapper.insert(any(User.class))).thenThrow(new DuplicateKeyException("uk_openid"));

        LoginVO vo = authService.login("c");

        assertEquals(9L, vo.userInfo().getId());
    }
}
```

- [ ] **Step 5: 跑测试，确认失败（编译失败即视为失败）**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-core -am test -Dtest=AuthServiceImplTest -DfailIfNoSpecifiedTests=false`
Expected: 编译失败，`找不到符号: 类 AuthServiceImpl`。

- [ ] **Step 6: 写实现 `AuthServiceImpl`**

不标 `@Transactional`：本方法只有「一次查询 + 最多一次插入 + 纯内存签 token」，没有需要原子性的多语句不变量；去掉事务也顺带避开「捕获 `DuplicateKeyException` 后事务已被标记 rollback-only」的隐患。`DuplicateKeyException` 由 MyBatis-Spring 的异常翻译把 MySQL 唯一键冲突转成，`uk_openid` 是它的触发条件。

```java
package com.leejie.xtx.core.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.leejie.xtx.common.constant.Constants;
import com.leejie.xtx.common.jwt.JwtUtils;
import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.entity.User;
import com.leejie.xtx.core.mapper.UserMapper;
import com.leejie.xtx.core.service.AuthService;
import com.leejie.xtx.wechat.service.WechatLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final WechatLoginService wechatLoginService;
    private final UserMapper userMapper;

    @Override
    public LoginVO login(String code) {
        String openid = wechatLoginService.login(code).openid();

        User user = findByOpenid(openid);
        if (user == null) {
            user = createUser(openid);
        }

        String token = JwtUtils.generate(Map.of(
                Constants.CLAIM_USER_ID, user.getId(),
                Constants.CLAIM_OPENID, openid
        ));
        return new LoginVO(token, UserVO.fromEntity(user));
    }

    private User findByOpenid(String openid) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenid, openid));
    }

    private User createUser(String openid) {
        User user = new User();
        user.setOpenid(openid);
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            // 同一 openid 并发首登：另一个请求先插成功并撞上 uk_openid。回读赢家那行即可，
            // 对调用方而言仍是一次普通登录，不该暴露成 500。
            return findByOpenid(openid);
        }
    }
}
```

- [ ] **Step 7: 跑测试，确认通过**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-core -am test -Dtest=AuthServiceImplTest -DfailIfNoSpecifiedTests=false`
Expected: `Tests run: 3, Failures: 0, Errors: 0`，BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
git add xtx-core/src/main/java/com/leejie/xtx/core/mapper/UserMapper.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/LoginReq.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/UserVO.java \
        xtx-core/src/main/java/com/leejie/xtx/core/dto/LoginVO.java \
        xtx-core/src/main/java/com/leejie/xtx/core/service/AuthService.java \
        xtx-core/src/main/java/com/leejie/xtx/core/service/impl/AuthServiceImpl.java \
        xtx-core/src/test/java/com/leejie/xtx/core/service/impl/AuthServiceImplTest.java
git commit -m "feat(auth): 微信登录查建用户并签发 JWT"
```

---

### Task 3: 登录端点与文档收尾（xtx-api）

**Files:**
- Create: `xtx-api/src/main/java/com/leejie/xtx/api/controller/AuthController.java`
- Test: `xtx-api/src/test/java/com/leejie/xtx/api/controller/AuthControllerTest.java`
- Modify: `CLAUDE.md`（「当前未完成 / 易踩坑」一节里微信登录的描述）

**Interfaces:**
- Consumes: `AuthService.login(String)`、`LoginReq`、`LoginVO`（Task 2）
- Produces: `POST /auth/login`（完整路径 `/api/auth/login`，因 `server.servlet.context-path=/api`）

- [ ] **Step 1: 写失败测试 `AuthControllerTest`**

用 `standaloneSetup` 不启 Spring 上下文：既避开「完整上下文需要 MySQL/Redis」的前提，也避开 `SecurityConfig`。这里只钉住 URL、请求体反序列化与响应信封形状（前端契约）。

```java
package com.leejie.xtx.api.controller;

import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.dto.UserVO;
import com.leejie.xtx.core.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    @Test
    @DisplayName("POST /auth/login 返回 {code,data:{token,userInfo}} 信封")
    void login_returnsTokenEnvelope() throws Exception {
        AuthService authService = mock(AuthService.class);
        UserVO user = new UserVO();
        user.setId(1L);
        user.setNickname("兔");
        when(authService.login("code-1")).thenReturn(new LoginVO("jwt-token", user));

        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(authService)).build();

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"code-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("jwt-token"))
                .andExpect(jsonPath("$.data.userInfo.id").value(1))
                .andExpect(jsonPath("$.data.userInfo.nickname").value("兔"));
    }
}
```

- [ ] **Step 2: 跑测试，确认失败（编译失败即视为失败）**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-api -am test -Dtest=AuthControllerTest -DfailIfNoSpecifiedTests=false`
Expected: 编译失败，`找不到符号: 类 AuthController`。

- [ ] **Step 3: 写 `AuthController`**

```java
package com.leejie.xtx.api.controller;

import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.LoginReq;
import com.leejie.xtx.core.dto.LoginVO;
import com.leejie.xtx.core.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口。
 *
 * <p>挂在 /auth/** 下，SecurityConfig.PUBLIC_PATHS 已放行，登录本身不需要 token。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "微信小程序登录")
    public R<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return R.ok(authService.login(req.code()));
    }
}
```

- [ ] **Step 4: 跑测试，确认通过**

Run: `JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn -pl xtx-api -am test -Dtest=AuthControllerTest -DfailIfNoSpecifiedTests=false`
Expected: `Tests run: 1, Failures: 0, Errors: 0`，BUILD SUCCESS。

- [ ] **Step 5: 手工验证 Review Focus 第 4、5 条**

先起依赖与两个服务（需本地 MySQL + Redis，见 `docker/docker-compose.yml`）：

```bash
JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn install -DskipTests
cd xtx-api && JAVA_HOME="D:/Program Files/Java/jdk-21.0.11" mvn spring-boot:run
```

第 4 条（免认证可达）：**不带** `Authorization` 头请求，期望拿到业务响应而非 401。

```bash
curl -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"code":"definitely-invalid-code"}'
```

Expected: HTTP 200，body 形如 `{"code":400,"msg":"微信登录失败: ...","data":null}`（走到微信调用后被拒），**不是** `401`。

第 5 条（配置缺失）：把 `xtx-api/src/main/resources/application.yml` 里 `wechat.app-secret` 临时改成一个错值后重启，再打同一个请求。
Expected: 返回 `code:400` 且 msg 含微信的 errcode 描述（如 `invalid appsecret`），能据此定位到配置，而不是无信息的 500。验证完把配置改回。

- [ ] **Step 6: 更新 `CLAUDE.md` 的「当前未完成 / 易踩坑」**

把第一条里「尚未落地的是微信登录」整段替换为已落地的现状，说明：`POST /auth/login`（入参 `{code}`，出参 `R<LoginVO>`，`token` + 精简 `userInfo`）已可用，实现链路为 `AuthController → AuthServiceImpl → WechatLoginServiceImpl(WxJava) → UserMapper → JwtUtils`；`GET /api/auth/dev-token` 仍保留为 dev profile 下的调试入口。其余未完成项（AI 生成、敏感配置外置）保持不变。

- [ ] **Step 7: 提交**

```bash
git add xtx-api/src/main/java/com/leejie/xtx/api/controller/AuthController.java \
        xtx-api/src/test/java/com/leejie/xtx/api/controller/AuthControllerTest.java \
        CLAUDE.md
git commit -m "feat(auth): 新增 POST /auth/login 端点并同步文档"
```

---

## 前端对接契约（供 uniapp 侧使用，不在本仓库内实现）

```text
POST /api/auth/login
Content-Type: application/json
Body:      { "code": "<wx.login() 返回的 code>" }

200 →
{
  "code": 200,
  "msg": "success",
  "data": {
    "token": "<JWT>",
    "userInfo": { "id": 1, "nickname": null, "avatarUrl": null }
  }
}
```

- 前端拿 `code`：`uni.login({ provider: 'weixin' })` → `res.code`（一次性、约 5 分钟有效，别缓存复用）。
- 存 token：`uni.setStorageSync('token', data.token)`；后续请求头 `Authorization: Bearer <token>`（`JwtAuthFilter` 只认 `Bearer ` 前缀）。
- 首登用户 `nickname` / `avatarUrl` 为 `null`，需前端引导填写（头像昵称填写能力，具体接口以微信官方文档为准）。
- 失败时 `code` 为 `400`（code 无效）等，`msg` 可直接展示。

---

## Self-Review

**1. Spec coverage**
- spec 流程 1「微信登录」时序图的每一步都有落点：`wx.login`→code（前端契约节）、`POST /auth/login`（Task 3）、jscode2session（Task 1）、查/建 user（Task 2）、签发 JWT（Task 2）、返回 `{token, userInfo}`（Task 2/3）。无缺口。
- spec 未定义的部分（session_key 存储、并发首登、错误码映射）由用户决策与 Review Focus 覆盖。

**2. Placeholder scan**：无 TBD / TODO / 「类似 Task N」；每个代码步骤都是可直接粘贴的完整文件。

**3. Type consistency**：`WxLoginResult(String openid, String sessionKey, String unionid)` 在 Task 1 定义、Task 2 测试按 `openid()` 访问，一致；`LoginVO(String token, UserVO userInfo)` 在 Task 2 定义、Task 3 测试按 `new LoginVO(...)` 构造并以 `data.userInfo` 断言，一致；`AuthService.login(String)→LoginVO`、`AuthServiceImpl(WechatLoginService, UserMapper)` 构造器与 Task 1/2 的产物签名一致。

**4. Review Focus**：第 1、2、3 条各自在 Task 1 / Task 2 有对应测试；第 4、5 条无可行自动化（需完整上下文 + 真实微信），已写成 Task 3 Step 5 的手工验证。
