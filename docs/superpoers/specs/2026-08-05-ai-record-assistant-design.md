# AI 记录助手 - 设计文档

## 元信息

| 项 | 值 |
|----|----|
| 日期 | 2026-08-05 |
| 作者 | leeji |
| 状态 | 设计阶段,待评审 |
| 技术栈 | uniapp + Spring Boot 3 + Spring Security + JWT + MyBatis-Plus + MySQL + Redis + MinIO + Spring AI + 通义千问 VL |

## 背景与目标

做一个微信小程序,作为 Java 后端开发转全栈的简历项目。核心目标是展示 **vibe coding 的"一人成军"能力**--用 AI 工具链(原型图生成、前端代码生成、提示词工程)从 0 到 1 独立完成一个完整产品。

不追求高并发等复杂后端(2.0 再考虑),重点是**完整产品闭环 + AI 辅助开发 + AI 能力集成**的叙事。

## 1. 产品定位与功能边界

### 一句话定位

随手记文字 + 拍照(生活/学习),AI 自动整理成日记、周记、学习总结、复盘等结构化内容。

### 核心场景

1. **生活**:日常琐事、心情、消费,文字或拍小票/照片
2. **学习**:新知识、读书笔记,文字或拍书页/笔记
3. **生成报告**:选时间段 + 分类 + 模板,AI 流式生成

### 记录分类(MVP 固定 2 类)

`生活(LIFE)` / `学习(STUDY)`,不支持自定义(2.0 再开放)。

### 报告模板(MVP 4 个)

| 模板 | 典型时间段 | 用途 |
|------|------------|------|
| `DIARY`(日记) | 单天 | 当天生活记录整理 |
| `WEEKLY`(周记) | 一周 | 本周生活回顾 |
| `STUDY_SUMMARY`(学习总结) | 自定义 | 一段学习期的总结 |
| `REVIEW`(复盘) | 自定义 | 针对某件事/某段时间的深度复盘 |

### MVP 核心功能

- 微信登录(`wx.login` + JWT)
- 记录管理:新增(文字/拍照 + 选分类)、列表(分类筛选 + 时间倒序)、编辑、删除
- AI 生成:选时间段 + 分类(可选) + 模板,流式输出
- 报告管理:保存、列表、详情、复制、分享微信好友
- 图片上传 MinIO

### 明确不做(YAGNI)

- ❌ 工作/企业场景(已有企业平台)
- ❌ 多人协作 / 团队
- ❌ 付费会员
- ❌ PC 端 / H5
- ❌ AI 对话聊天
- ❌ 统计图表 / 关键词云(2.0)
- ❌ 自定义分类 / 自定义模板(2.0)
- ❌ 多模型切换 UI(后端配死通义千问 VL)
- ❌ 多模态报告生成(MVP 报告只用文字,图片不参与生成)

### 前端页面(5 个)

1. 记录列表 tab(首页,顶部"全部/生活/学习"筛选 + 时间倒序列表)
2. 新增/编辑记录页(文字输入 + 拍照 + 分类选择)
3. 生成报告页(选时间段 + 选分类 + 选模板 + 流式输出 + 保存)
4. 报告列表 tab + 报告详情页
5. 我的(登录态、关于)

## 2. 系统架构

### 整体架构(5 层)

```
┌──────────────────────────────────────────┐
│  微信小程序(uniapp + Vue3 + Pinia)       │
│  记录列表/新增/编辑 · 报告生成(流式) · 报告 │
└──────────────┬───────────────────────────┘
               │ HTTPS + JWT
               ▼
┌──────────────────────────────────────────┐
│  Nginx(反向代理 + HTTPS 终止 + 域名)     │
└──────────────┬───────────────────────────┘
               ▼
┌──────────────────────────────────────────┐
│  Spring Boot 3 后端                        │
│  ┌────────────────────────────────────┐  │
│  │ Spring Security(资源过滤链)         │  │
│  │  └─ JwtAuthFilter(自定义Filter)     │  │
│  │ Controller: 用户/记录/报告/AI生成   │  │
│  │ Service:    User/Record/Report/Ai/  │  │
│  │            Oss/PromptManager        │  │
│  │ 基础设施:   SecurityContextHolder    │  │
│  │            MyBatis-Plus / Redis     │  │
│  │            Spring AI                │  │
│  └────────────────────────────────────┘  │
└────┬──────────┬──────────┬───────────────┘
     ▼          ▼          ▼
 ┌───────┐ ┌────────┐ ┌──────────────┐
 │ MySQL │ │ Redis  │ │ MinIO        │
 │业务数据│ │限流/缓存│ │ 图片存储      │
 └───────┘ └────────┘ └──────┬───────┘
                             │ 多模态 API
                             ▼
                  ┌─────────────────────┐
                  │ 通义千问 VL(百炼平台)│
                  │ 文本生成 + 拍照识别   │
                  └─────────────────────┘
```

### 各层技术选型

| 层 | 技术 | 选型理由 |
|----|------|----------|
| 前端 | uniapp + Vue3 + Pinia + uview-plus | 在学 uniapp;Pinia 轻量适合小程序 |
| 接入 | Nginx + HTTPS + 备案域名 | 小程序强制 HTTPS + 备案 |
| 后端 | Spring Boot 3 + Spring Security + JWT | Java 主场;Spring Security 认证+授权框架,官方推荐 |
| ORM | MyBatis-Plus + MySQL | Java 生态主流 |
| 缓存 | Redis | AI 接口限流 + 生成报告分布式锁(MVP 不做业务缓存) |
| AI | Spring AI(spring-ai-alibaba-starter)+ 通义千问 VL | 统一封装、流式、多模态;简历加分 |
| 存储 | MinIO(本地开发) | 类 OSS 的对象存储,兼容 S3 API,本地调试方便;部署可切阿里云 OSS |
| 部署 | 阿里云轻量服务器 + Docker | 50-100 元/月,够用 |

### 4 个关键架构决策

1. **Spring AI 而非裸 HTTP**:统一封装通义千问 VL 的文本/多模态/流式调用,切换模型只改配置代码不动。简历上"基于 Spring AI 集成多模态大模型"比"OkHttp 调 API"亮。
2. **通义千问 VL 而非其他**:阿里云生态(OSS + 百炼平台同账号同地域,内网调用快且省流量费)、价格便宜、Spring AI 有官方 starter、文本 + 多模态一个模型搞定。
3. **Redis 做限流**:AI 接口按 token 收费,必须限制单用户每日生成次数(默认 10 次/天),防止被刷爆账单。工程化亮点。

4. **Spring Security 无状态 JWT 集成**:替换自定义 JwtAuthInterceptor,走 Spring Security 标准 Filter 链。统一认证抽象,Service 层通过 `SecurityContextHolder` 获取当前用户,不依赖 Request 传参;为后续管理后台 RBAC 预留扩展点。

### Spring Security 集成设计

**依赖**:`spring-boot-starter-security`(Spring Boot 3.4.0 已自带版本管理)。

**架构**:
```
SecurityFilterChain(无状态)
  ├── CorsFilter(允许小程序域名)
  ├── JwtAuthFilter(自定义 OncePerRequestFilter)
  │   ├── 从 Authorization header 提取 JWT
  │   ├── 解析 userId -> UsernamePasswordAuthenticationToken
  │   └── 注入 SecurityContextHolder
  └── ExceptionTranslationFilter(401 处理)
```

**关键配置**:
- 会话管理:`SessionCreationPolicy.STATELESS`(小程序无 Cookie)
- 放行:`POST /api/auth/login`(微信登录接口)
- 其余:全部需认证,自动 401
- 关闭 CSRF(纯 API,无 Cookie)

**Service 层获取用户 ID**:
```java
// 不再需要 Controller 传参,任何地方直接获取
Long userId = SecurityUtils.getCurrentUserId();
```

**CORS 配置**:通过 Spring Security 的 `CorsConfigurationSource` 统一管理,替代 `WebMvcConfig` 中的跨域配置,后续可在此基础上加 IP 限流。

**与现有代码的桥接**:`JwtUtils`(xtx-common 已有)继续使用,`JwtAuthFilter` 复用其解析逻辑;`GlobalExceptionHandler` 保持不变,Spring Security 的 `AccessDeniedException` 映射到 `R.fail(403)`。

### 提示词管理模块(核心资产)

每个生成场景对应一个 `.st` 提示词模板文件,模块化管理(类似"skill"机制):

```
src/main/resources/prompts/
├── image-extract.st          # 拍照识别:图片->文字记录
├── diary.st                  # 日记模板
├── weekly.st                 # 周记模板
├── study-summary.st          # 学习总结模板
└── review.st                 # 通用复盘模板
```

`.st` 文件用 StringTemplate 语法,支持变量插值(`{{records}}`、`{{startDate}}` 等)。切换模板只改参数,代码不动,提示词单独维护,符合 vibe coding 的迭代节奏。

### 部署形态(单机够用)

- 1 台阿里云轻量服务器(2 核 2G 或 4G)
- Docker 跑后端 jar + MySQL + Redis + MinIO(docker-compose)
- Nginx 跑宿主机或容器,做 HTTPS + 反代
- 通义千问用阿里云托管服务,不在自己服务器

## 3. 数据模型

### 表 1:`user`(用户表)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `openid` | VARCHAR(64) | 微信 openid,唯一索引 |
| `nickname` | VARCHAR(64) | 昵称 |
| `avatar_url` | VARCHAR(512) | 头像 URL |
| `daily_quota` | INT | 每日 AI 生成配额,默认 10 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |

### 表 2:`record`(记录表)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | 用户 ID,索引 |
| `category` | VARCHAR(16) | 分类:`LIFE` / `STUDY` |
| `content` | TEXT | 文字内容(必填) |
| `images` | JSON | 图片 URL 数组(JSON 类型,MySQL 5.7+,**单条最多 9 张**) |
| `record_date` | DATE | 记录日期(用户可选,支持补记历史),索引 |
| `source` | VARCHAR(16) | 来源:`MANUAL` 手输 / `IMAGE` 拍照识别 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |
| `deleted` | TINYINT | 逻辑删除(MyBatis-Plus 注解) |

**索引**:`(user_id, record_date, deleted)` 联合索引--生成报告按时间段查询的核心路径。

### 表 3:`report`(报告表)

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键 |
| `user_id` | BIGINT | 用户 ID,索引 |
| `template` | VARCHAR(32) | 模板:`DIARY` / `WEEKLY` / `STUDY_SUMMARY` / `REVIEW` |
| `title` | VARCHAR(128) | 报告标题 |
| `content` | LONGTEXT | 报告内容(Markdown) |
| `start_date` / `end_date` | DATE | 覆盖的时间段 |
| `category` | VARCHAR(16) | 筛选分类:`LIFE` / `STUDY` / `ALL` |
| `record_count` | INT | 基于多少条记录生成 |
| `model` | VARCHAR(64) | 使用的模型名(可追溯) |
| `tokens_used` | INT | 本次消耗 token 数 |
| `created_at` / `updated_at` | DATETIME | 时间戳 |
| `deleted` | TINYINT | 逻辑删除 |

**索引**:`(user_id, created_at, deleted)`--报告列表按时间倒序查询。

### Redis key 设计

| Key | 值 | TTL | 用途 |
|-----|----|-----|------|
| `quota:user:{userId}:{yyyyMMdd}` | INT 计数 | 到当天 23:59:59 | 每日 AI 生成配额,超限拒绝 |
| `lock:generate:{userId}` | 1 | 30 秒 | 生成报告分布式锁,防并发重复提交 |
| `wxsession:{sessionId}` | userId | 7 天 | 微信登录态缓存(MVP 不做,JWT 无状态;2.0 如需强制下线再启用) |

### 4 个关键数据模型决策

1. **模板不建表**:MVP 4 个模板用 Java 枚举 + `.st` 提示词文件管理,2.0 做自定义模板时再建 `template` 表。避免过度设计。
2. **图片用 JSON 字段,不拆子表**:`images` 存 URL 数组,查询简单。拆 `record_image` 子表是过度设计(MVP 不查"哪些记录用了某图")。
3. **`record_date` 独立于 `created_at`**:用户可以今天补记昨天的笔记,业务日期和创建日期必须分开。生成报告按时间段筛选的关键。
4. **配额用 Redis 计数,不落库**:TTL 自动过期,高性能,不用定时任务清零。`user.daily_quota` 只存配置上限,当日已用次数全走 Redis。

### 不加 `create_by` / `update_by` 的理由

本项目是 C 端单用户场景,数据按 `user_id` 隔离,`create_by` 必然等于 `user_id`,加了是冗余。`created_at` / `updated_at`(时间)保留,`create_by` / `update_by`(人)不加。企业级项目里多租户、有后台代操作、需要审计的场景才需要加(用 MyBatis-Plus 的 `MetaObjectHandler` 自动填充)。

### ER 关系

```
user (1) ──── (N) record
user (1) ──── (N) report
record 与 report 无直接关联(报告基于时间段+分类查记录生成,生成后内容独立存 report.content)
```

## 4. 核心流程与数据流

### 流程 1:微信登录

```
小程序                  后端                      微信
  |--wx.login()-->|      |                        |
  |<-code---------|      |                        |
  |--POST /auth/login-->|                        |
  |  {code}        |----jscode2session---------->|
  |                |<---openid+session_key-------|
  |                |查/建user表                  |
  |                |签发JWT(userId)              |
  |<--{token,userInfo}---|                        |
  |存token到本地   |      |                        |
```

### 流程 2:新增记录(统一,文字 + 拍照)

记录结构统一:每条记录都有文字 `content`(必填) + 可选图片 `images`(0-N 张),`source` 标记文字是手输还是 AI 识别来的。

```
1. (可选)上传图片: POST /api/upload/image (multipart)
   后端: 存MinIO -> 返回 {imageUrl}
   可重复调用,上传 0-N 张

2. (可选)对某张图片 AI 识别成文字:
   POST /api/ai/extract {imageUrl}
   后端: 加载 image-extract.st 提示词
        -> Spring AI 多模态调用
        -> 返回 {extractedText}
   小程序: 把识别文字填入 content 输入框,用户可编辑

3. 新增记录(统一接口):
   POST /api/records {
     category: LIFE/STUDY,
     content: "文字内容",       // 必填
     images: ["url1","url2"],  // 可选,0-N张
     source: MANUAL/IMAGE,     // 标记来源
     recordDate: "2026-08-05"
   }
```

**3 种典型用法**:
- 纯文字记录:`content` 有字,`images` 空,`source=MANUAL`
- 文字 + 配图(生活笔记常见):`content` 有字,`images` 有图,`source=MANUAL`
- 拍照 AI 识别:先上传图->AI 识别->文字填入 content->图片也存,`source=IMAGE`

### 流程 3:生成报告(核心,流式输出)

```
小程序                    后端                     存储/AI
  |                        |                        |
  |--POST /reports/generate-->|                     |
  |  {start,end,cat,template}|                      |
  |                        |--查配额-->Redis        |
  |                        |  (超限返回429)         |
  |                        |--查记录-->MySQL        |
  |                        |  (0条返回错误)         |
  |                        |--加载prompts/{tpl}.st  |
  |                        |--拼装Prompt            |
  |                        |--(预检)msgSecCheck-->微信|
  |                        |--流式调AI-->通义VL     |
  |<--SSE chunk1-----------|<--chunk1---------------|
  |<--SSE chunk2-----------|<--chunk2---------------|
  |     ...                |                        |
  |<--SSE [DONE]-----------|<--done-----------------|
  |                        |--内容安全检测-->微信   |
  |                        |--写report表-->MySQL    |
  |                        |  (content,tokens,model)|
  |                        |--配额+1-->Redis        |
  |<--{reportId}-----------|                        |
```

### 生成报告的关键步骤

1. **配额预检**:Redis 取当日已用次数,`>= daily_quota` 直接 429,不调 AI(省钱)
2. **分布式锁**:`SETNX lock:generate:{userId}`,获取不到返回"正在生成中",防并发重复提交
3. **空记录拦截**:时间段内无记录直接返回 422,不调 AI(省钱)
4. **记录数限制**:超过 50 条提示"缩短时间段",避免输入 token 过大
5. **流式输出**:后端用 `SseEmitter`,Spring AI 的 `.stream()` 返回 `Flux<String>`,逐 chunk 推给小程序;小程序用 `uni.request` 的 `enableChunked: true` 接收
6. **双端内容安全**:生成前对拼装的提示词过 `msgSecCheck`(防违规输入),生成后对输出内容再过一次(微信审核硬性要求)
7. **落库 + 配额**:流结束后才写 `report` 表 + Redis 计数 +1,失败则回滚

### AI 延迟分析

| 阶段 | 耗时 | 说明 |
|------|------|------|
| 查数据库 | <100ms | 15 条记录很快 |
| 拼 Prompt + msgSecCheck 预检 | 200-500ms | 微信内容安全接口 |
| **首 token 延迟(TTFT)** | **0.5-2 秒** | 模型理解输入 + 开始生成 |
| **生成阶段** | **5-15 秒** | 取决于输出长度,每秒约 50-100 token |
| 写库 + 配额+1 | <100ms | 流结束后 |

**总耗时约 6-18 秒**(一篇 500-1000 字的周记)。用 SSE 流式输出把体验做起来,1-2 秒就看到第一个字,像 ChatGPT 那样逐字流出。

### 成本估算

- 单次周记生成:输入约 2000 token + 输出约 1000 token = 3000 token
- 通义千问 VL 约 0.008 元/千 token -> **单次约 0.024 元**
- 每日 10 次配额 -> 单用户每日成本上限约 **0.24 元**
- 100 个日活用户 -> 每月 AI 成本约 720 元,可控

### 数据流小结

| 流程 | 写 MySQL | 写 Redis | 调 AI | 调微信 | 存储 |
|------|----------|----------|-------|--------|--------|
| 登录 | 读+写 user | ❌ | ❌ | ✅ jscode2session | ❌ |
| 上传图片 | ❌ | ❌ | ❌ | ❌ | ✅ 存图 |
| 新增记录 | 写 record | ❌ | ❌ | ❌ | ❌ |
| (可选)AI 识别文字 | ❌ | ❌ | ✅ 多模态 | ❌ | ❌ |
| 生成报告 | 写 report | ✅ 配额+1 | ✅ 流式文本生成 | ✅ msgSecCheck | ❌ |
| 查看报告 | 读 report | ❌ | ❌ | ❌ | ❌ |

## 5. API 设计

### 统一约定

**响应格式**(统一使用框架 `R<T>` 格式):
```json
{ "code": 200, "msg": "success", "data": {...} }
```

**鉴权**:通过 Spring Security Filter 链统一拦截。除 `POST /api/auth/login` 外,所有接口需 `Authorization: Bearer {jwt}` 头。JWT 失效或缺失自动返回 401。

**错误码**:

| code | 含义 | 触发场景 |
|------|------|----------|
| 200 | 成功 | 正常 |
| 401 | 未登录/Token 失效 | JWT 校验失败 |
| 403 | 无权限 | 越权访问 |
| 429 | 配额超限或并发锁 | 当日 AI 生成次数用完 / 正在生成中 |
| 430 | 内容不合规 | msgSecCheck 检测到违规 |
| 422 | 业务校验失败 | 时间段无记录、参数错误等 |
| 500 | 服务器错误 | 兜底 |

**分页**:`page` 从 1 开始,`size` 默认 10。

### API 列表

| 分组 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 认证 | POST | `/api/auth/login` | 微信登录,返 JWT |
| 记录 | POST | `/api/records` | 新增记录 |
| 记录 | GET | `/api/records?page&size&category&start&end` | 列表(分类+时间段筛选) |
| 记录 | GET | `/api/records/{id}` | 记录详情 |
| 记录 | PUT | `/api/records/{id}` | 编辑记录 |
| 记录 | DELETE | `/api/records/{id}` | 删除(逻辑删除) |
| 上传 | POST | `/api/upload/image` | 上传图片到 MinIO,返 URL |
| AI | POST | `/api/ai/extract` | 拍照识别文字(可选辅助) |
| 报告 | POST | `/api/reports/generate` | 流式生成报告(SSE) |
| 报告 | GET | `/api/reports?page&size` | 报告列表 |
| 报告 | GET | `/api/reports/{id}` | 报告详情 |
| 报告 | PUT | `/api/reports/{id}` | 保存用户编辑后的内容 |
| 报告 | DELETE | `/api/reports/{id}` | 删除报告 |
| 用户 | GET | `/api/user/profile` | 个人信息 |
| 用户 | GET | `/api/user/quota` | 当日配额查询 |

### 关键接口详情

**1. 微信登录**
```
POST /api/auth/login
Req:  { "code": "wx.login的code" }
Resp: { "token": "xxx", "userInfo": {"id":1,"nickname":"...","avatarUrl":"...","dailyQuota":10} }
```

**2. 新增记录**
```
POST /api/records
Req:  {
  "category": "LIFE",
  "content": "今天去公园散步,天气很好",
  "images": ["https://minio.example.com/bucket/a.jpg"],
  "source": "MANUAL",
  "recordDate": "2026-08-05"
}
Resp: { "id": 101 }
```

**3. 生成报告(SSE 流式)**
```
POST /api/reports/generate
Header: Accept: text/event-stream
Req:  { "startDate":"2026-08-01", "endDate":"2026-08-05", "category":"LIFE", "template":"WEEKLY" }

Response (SSE 流):
data: {"chunk":"本周的生活记录整理如下"}
data: {"chunk":"周一去了公园..."}
...
data: {"done":true, "reportId":42, "tokensUsed":1280}

错误中途:
data: {"error":"内容不合规,已拦截"}
```

**4. 配额查询**
```
GET /api/user/quota
Resp: { "dailyQuota":10, "usedToday":3, "remaining":7 }
```

### 3 个 API 设计要点

1. **SSE 而非 WebSocket**:生成报告是单向推送(后端->前端),SSE 够用且更简单;WebSocket 双向通信是过度设计。小程序用 `uni.request({enableChunked:true})` 接收。
2. **错误码语义化**:`429`(配额)、`430`(内容不合规)、`422`(业务校验)分开,前端能针对性提示。`430` 是微信审核要求的"违规内容拦截"必须处理。
3. **配额查询独立接口**:生成报告前前端先查配额,配额不足直接禁用按钮,不浪费一次 AI 调用。

## 6. 错误处理与边界

### 错误处理矩阵

| 场景 | 触发时机 | 处理 | 错误码 |
|------|----------|------|--------|
| JWT 过期/无效 | 每次请求 Spring Security Filter 校验 | 返回 401,前端跳登录 | 401 |
| 配额超限 | 生成报告前 Redis 检查 | 拒绝调用 | 429 |
| Redis 不可用 | 配额检查时 | **降级**:允许调用 + 告警日志 | - |
| 时间段无记录 | 生成报告前查记录数 | 返回"该时间段无记录" | 422 |
| 记录数 >50 条 | 生成报告前 | 提示"缩短时间段" | 422 |
| AI 超时(>30s) | SseEmitter 超时 | 推 error 事件,前端显示重试 | 500 |
| AI 接口报错 | 通义返回 5xx | 重试 1 次,仍失败返回错误 | 500 |
| msgSecCheck 预检失败 | 生成前对输入检测 | 拦截 | 430 |
| msgSecCheck 生成后失败 | 流结束后对输出检测 | 不落库,提示重试 | 430 |
| 流式中断 | 前端检测 SSE 断开 | 显示"连接中断,重试" | - |
| 越权访问 | 查/改时校验 user_id | 返回 404(不暴露存在) | 404 |
| 图片过大(>5MB) | 上传前 | 拒绝 | 422 |
| 重复提交生成 | 连续点按钮 | 前端禁用 + 后端幂等锁 | 429 |

### 4 个关键边界

**1. 并发生成(幂等)**

用 Redis 分布式锁防重复提交:
```
key:   lock:generate:{userId}
TTL:   30 秒
获取:  SETNX,获取不到返回"正在生成中,请稍候"
释放:  生成结束(成功/失败)后 DEL
```

**2. Redis 降级**

Redis 挂了不能让整个生成功能不可用。降级策略:
- 允许调用(不阻断用户)
- 写告警日志(便于事后补对账)
- 数据库 `report` 表照常记录本次生成

**3. 越权访问(防 IDOR)**

所有按 id 查/改的接口,必须带 user_id 条件:
```java
// 错误:只按 id 查,用户可构造别人的 id
recordMapper.selectById(id);

// 正确:带 user_id 校验
recordMapper.selectOne(new LambdaQueryWrapper<Record>()
    .eq(Record::getId, id)
    .eq(Record::getUserId, currentUserId));
```
查不到返回 404(不返回 403,避免暴露"存在但无权限"的信息)。

**4. msgSecCheck 双端检测**

微信对 AI 生成内容有强制要求:输入和输出都要过 `msgSecCheck`。
- **生成前**:对拼装的提示词(含用户记录内容)检测,违规直接 430
- **生成后**:对 AI 输出的报告内容检测,违规不落库、不计配额、提示用户

### 安全边界

| 维度 | 措施 |
|------|------|
| XSS | 报告内容是 Markdown,前端用 `towxml` 等安全解析库渲染,不执行脚本 |
| 输入校验 | 所有参数 `@Valid` 校验(长度、格式、枚举值),用 `@NotBlank` `@Size` 等 |
| SQL 注入 | MyBatis-Plus 参数化查询,禁止拼 SQL |
| 图片安全 | 大小 5MB 内,格式 jpg/png/webp,**用魔数校验而非扩展名**(防伪装) |
| 接口限流 | 除用户配额外,Nginx 层 IP 限流(如 60 次/分钟),防恶意刷接口 |
| 敏感信息 | API Key、MinIO 密钥存环境变量,不进 git;`.env` 加 `.gitignore` |

## 7. 测试策略

### 测试金字塔

```
       /\
      /  \  端到端(手动):关键流程跑通
     /----\
    /      \ 集成测试:API + 越权 + 配额 + 流式
   /--------\
  /          \ 单元测试:Service + 提示词拼装 + Mock AI
 /------------\
```

### 技术选型

| 层 | 工具 | 用途 |
|----|------|------|
| 单元测试 | JUnit 5 + Mockito | Service 层,Mock AI/Redis |
| 集成测试 | Spring Boot Test + MockMvc | Controller 到 DB 全链路 |
| 测试容器 | Testcontainers | 真实 MySQL/Redis,避免 Mock 失真 |
| 覆盖率 | JaCoCo | 生成报告,目标核心模块 60%+ |
| 前端 | 手动 checklist | 小程序自动化成本高,MVP 手动测 |

### 关键测试用例

| 模块 | 测试点 | 类型 | 简历亮点 |
|------|--------|------|----------|
| 登录 | code 换 openid、新用户自动创建、JWT 签发 | 集成 | 微信登录链路 |
| 记录 CRUD | 增删改查、分类筛选、时间段筛选、逻辑删除 | 集成 | - |
| **越权防护** | 用户 A 查用户 B 的记录返回 404 | 集成 | **IDOR 防护** |
| **配额控制** | 超限拒绝、当日计数 +1、跨天清零 | 单元 | **限流设计** |
| **Redis 降级** | Redis 不可用时允许调用 + 告警 | 单元 | **优雅降级** |
| **并发生成** | 同用户连点只成功 1 次(分布式锁) | 集成 | **接口幂等** |
| 生成报告 | 空记录返回 422、记录过多提示、流式输出完整 | 集成 | SSE 流式 |
| 内容安全 | 输入违规拦截(430)、输出违规不落库 | 集成 | 合规处理 |
| 提示词拼装 | 4 个模板的变量填充正确 | 单元 | 提示词工程 |
| AI Service | Mock ChatClient 返回,验证调用参数 | 单元 | AI 集成 |

### AI Mock 策略

测试时不真实调通义千问(慢 + 贵 + 不稳定),Mock `ChatClient`:

```java
@MockBean
private ChatClient.Builder chatClientBuilder;

@Test
void generateReport_success() {
    when(chatClient.prompt(any()).call().content())
        .thenReturn("本周生活记录整理...");

    ReportResult result = aiService.generateReport(...);

    assertThat(result.getContent()).contains("本周生活记录");
    assertThat(result.getTokensUsed()).isNotNull();
}
```

真实 AI 调用只在本地验收时手动测几次,不进自动化测试。

### 前端测试 checklist(手动)

```
□ 微信登录正常,token 持久化
□ 新增文字记录 + 选分类 + 选日期
□ 拍照上传图片,AI 识别文字填入
□ 记录列表按分类筛选、按时间倒序
□ 编辑/删除记录
□ 选时间段+分类+模板生成报告,流式逐字显示
□ 生成中禁用按钮(防重复提交)
□ 配额用完时按钮禁用 + 提示
□ 报告列表、详情、编辑保存
□ 分享报告给微信好友
□ 网络断开时友好提示
□ 内容违规时提示(430)
```

### 覆盖率目标

| 模块 | 目标 | 理由 |
|------|------|------|
| Service 层核心逻辑 | 70%+ | 配额、越权、提示词拼装 |
| Controller 层 | 50%+ | 关键 API 跑通 |
| AI Service | Mock 覆盖 80% | 验证调用逻辑 |
| 整体 | 50%+ | 务实,不追求 100% |

## 附录 A:简历亮点总结

本项目可在简历中突出以下工程化能力:

1. **AI 工程化**:基于 Spring AI 集成通义千问 VL 多模态大模型,实现文本生成 + 拍照识别,流式输出(SSE)
2. **提示词工程**:外部化 `.st` 模板文件管理 5 类提示词,支持变量插值,模块化设计
3. **接口幂等**:Redis 分布式锁防止并发生成重复扣配额
4. **限流与成本控制**:基于 Redis 的每日配额机制,做过成本估算(单次约 0.024 元)
5. **优雅降级**:Redis 不可用时降级策略,保证核心功能可用
6. **安全防护**:IDOR 越权防护、XSS 防护、内容安全双端检测(msgSecCheck)、图片魔数校验
7. **合规处理**:微信小程序 AI 生成内容审核要求落地
8. **vibe coding 能力**:全程 AI 辅助开发(原型图生成、前端代码生成、提示词迭代),独立完成全栈产品

## 附录 B:不做清单(YAGNI)

明确不在 MVP 范围,避免范围蔓延:

- 工作/企业场景、多人协作、付费会员、PC 端/H5
- AI 对话聊天、多模型切换 UI
- 统计图表、关键词云
- 自定义分类、自定义模板
- 多模态报告生成(图片参与报告生成,2.0)
- 小程序端直传 MinIO(MVP 走后端转发,2.0 用 STS 直传优化)
- WebSocket 双向通信(SSE 足够)
