# API 契约（前后端接口清单）

> 本文件是前后端所有 REST 接口的**唯一人工维护清单**。两端 `docs/api/` 各保存一份并保持同步。
> 后端是契约权威方；springdoc 生成的 `/v3/api-docs` 是接口的生成态参考，本文件补充人为决策：字段语义、取值边界、未冻结字段、变更记录。
> 改接口契约必须走根目录 CLAUDE.md 的「契约变更流程」：改后端 → 更新本文件 → 同步前端 `src/api/types.ts` + mock。

## 通用约定

- **Base URL**：`http://<host>:8080/api`（后端 context-path `/api`）
- **鉴权**：除 `/auth/**`、`/health/**`、swagger 外，一律要求请求头 `Authorization: Bearer <token>`。JWT 由 `POST /auth/login` 签发。
- **统一响应** `R<T> = { code, msg, data }`：`code=200` 成功；`401` 未认证（前端清登录态 → 静默重登 → 重放一次）；其余非 200 为业务失败，展示用 `msg`。
- **判断依据是 body 里的 `code`，不是 HTTP 状态码**：业务异常返回 HTTP 200 + `code != 200`；只有 Security 的 401/403 会同步设置 HTTP 状态码。
- **错误码**：`200` 成功 / `400` 参数异常 / `401` 未登录或登录已过期 / `403` 无权访问 / `422` 微信业务错误 / `500` 通用或系统错误。
- **字段命名**：JSON 一律 camelCase（`recordDate`、`avatarUrl`），两端都别期待 snake_case 字段。
- **Long 主键按字符串序列化**（雪花 id 超出 JS 精度，前端类型一律 `string`，禁止当 number）。
- **时间**：`createdAt`/`updatedAt` 为 ISO 字符串；日期字段 `recordDate` 为 `yyyy-MM-dd`。
- **分页**：请求 `pageNum`/`pageSize`（GET 查询串，非 JSON body），默认 1/10，上限 100；响应 `data = { list, total, pageNum, pageSize, hasNext }`。前端上拉加载只需 `if (hasNext) pageNum++`，不自己算总页数；`list` 为空时是 `[]` 不是 `null`。
- 图片来源：落库存 objectKey，出站时后端现签访问 URL（会过期，仅展示用），前端不持久化。

---

## 认证 / 用户

### POST /auth/login — 微信小程序登录（免鉴权）
- 入参 `{ code }`：`wx.login` 获取的临时 code（必填）。
- 出参 `data`：
  ```json
  { "token": "<jwt>", "userInfo": { "id": "1", "nickname": "轻记用户", "avatarUrl": "<访问URL>", "avatarKey": "<objectKey>", "signature": "", "email": "", "location": "" } }
  ```
- 说明：后端用 code 换 openid，首登自动建用户。微信接口失败返回业务码 422。

### GET /auth/dev-token — 签发调试 token（仅 dev profile，免鉴权）
- 入参 `?userId=1`；出参 `data` 为 token 字符串。H5 预览用（无 `wx.login`），生产不注册。

### GET /user/profile — 当前用户资料
- 鉴权：是。出参 `data` 即上面的 `userInfo`（`avatarUrl` 已现签可直接渲染）。

### PUT /user/profile — 更新当前用户资料
- 鉴权：是。入参：
  ```json
  { "nickname": "轻记用户", "signature": "", "email": "", "location": "", "avatarUrl": "" }
  ```
- 校验：nickname 必填 ≤12；signature ≤40；email ≤60 且格式正确；location ≤30；`avatarUrl` 传 **objectKey**（不是 URL），空串 = 清空头像。

---

## 字典

### GET /dict/{typeCode} — 查询字典项（仅启用项）
- 鉴权：是。出参 `data` 是**裸数组**（不分页、不套 list），后端已按 `sortOrder` 升序：
  ```json
  [ { "key": "note_label_xxx", "label": "生活", "sortOrder": 1, "extra": { "color": { "light": "#…", "dark": "#…" } } } ]
  ```
- 说明：`extra` 为类型专属属性，后端原样透传不拍平；`note_label` 是标签字典类型码，前端冷启动只拉一次。

---

## 文件（图片两段式直传 MinIO）

### POST /file/presign — 获取图片预签名上传表单
- 鉴权：是。入参 `{ contentType, size, originalFilename? }`（contentType/size 必填，size>0）。
- 出参 `data`：
  ```json
  { "postUrl": "<MinIO POST 地址>", "formData": { "key": "...", "policy": "...", "x-amz-*": "..." }, "objectKey": "img/42/2026/10/06/uuid.jpg", "expiresAt": 1700000000000 }
  ```
- 说明：前端用 `uni.uploadFile` **直传 MinIO**（`url=postUrl`，`name="file"`，formData 原样全量转发），**不带 Authorization**。上传成功后文件为 TEMP 状态，24h 内被记录/资料引用才转正，超时由后端清理。

### POST /file/upload — 非图片代理上传
- 鉴权：是。multipart 表单字段 `file`。出参 `data = { objectKey, originalFilename, contentType, size }`。

### GET /file/url — 换取访问 URL
- 鉴权：是。入参 `?objectKey=<objectKey>&download=false`。出参 `data` 为现签 URL 字符串（objectKey 带斜杠，走查询串不走路径变量）。

### DELETE /file — 删除文件
- 鉴权：是。入参 `?objectKey=<objectKey>`。出参 `R<Void>`。

---

## 记录（Record）— 前端编辑器 / 列表 / 详情已对齐

### POST /record/create — 创建记录
- 鉴权：是。入参：
  ```json
  { "title": "标题(空=无标题)", "label": "note_label 的 key(空=未分类)", "content": "正文", "images": ["<objectKey>"], "recordDate": "2026-10-06" }
  ```
- 校验：`recordDate` 必填；title/content 至少一项非空（service 校验）。出参 `data` 为新记录 id（字符串）。

### GET /record/getDetail/{id} — 记录详情
- 鉴权：是。出参 `data`（`images` 已换签为访问 URL，仅展示）：
  ```json
  { "id": "1", "title": "", "label": "note_label_xxx", "content": "", "images": ["<访问URL>"], "recordDate": "2026-10-06", "createdAt": "<ISO>", "updatedAt": "<ISO>" }
  ```

### PUT /record/update — 更新记录
- 鉴权：是。入参 = `RecordVO` 的 `{ id, title, label, content, images, recordDate }`。
- 说明：`images` 传 `null` = 不动附件；传 `[]` = 清空。出参 `R<Void>`。

### GET /record/page — 分页查询记录
- 鉴权：是。入参 `?pageNum=&pageSize=&label=`；`label` 空 = 不过滤。出参 `data = { list: RecordVO[], total, pageNum, pageSize, hasNext }`。

### DELETE /record/{id} — 删除记录
- 鉴权：是。**物理删除，无回收站，不可恢复**。出参 `R<Void>`。

---

## 报告（Report）

> **后续迭代预留**：AI 基于笔记生成报告（日记/周记/学习总结/复盘）的接口。前端暂未接入，先按后端实况记录，正式设计时再定。

### POST /report — 创建报告
- 鉴权：是。入参：`{ template, title?, content?, startDate?, endDate?, category?, recordCount?, model?, tokensUsed? }`；`template` 必填。
- 枚举：`template ∈ { DIARY, WEEKLY, STUDY_SUMMARY, REVIEW }`；`category ∈ { LIFE, STUDY, ALL }`。出参 `data` 为新报告 id。

### GET /report/{id} — 报告详情
- 鉴权：是。出参 `data`：
  ```json
  { "id": "1", "template": "DIARY", "title": "", "content": "<Markdown>", "startDate": "2026-10-01", "endDate": "2026-10-06", "category": "ALL", "recordCount": 12, "model": "qwen-...", "tokensUsed": 1234 }
  ```

### PUT /report — 更新报告
- 鉴权：是。入参 = 详情字段 + `id`。出参 `R<Void>`。

### DELETE /report/{id} — 删除报告
- 鉴权：是。出参 `R<Void>`。

### GET /report/page — 分页查询报告
- 鉴权：是。入参 `?pageNum=&pageSize=`。出参 `data = { list: ReportVO[], total, pageNum, pageSize, hasNext }`。

---

## 过渡接口（后端暂未落地，是否保留待决策）

> 背景：前端最初由 AI 独立生成（基于原型/文档，未结合后端接口设计），这两个接口后端从没实现，当前由 mock 支撑。**是否保留待决策，近期可能清理。**

| 方法 | 路径 | 前端调用处 | 说明 |
|---|---|---|---|
| GET | `/search?q=&tag=&pageNum=&pageSize=` | `src/api/modules/search.ts` | 服务端搜索（返回 `highlights`）。若保留则需后端实现，或并入 `/record` 筛选 |
| GET | `/tags` | `src/api/modules/tag.ts` | 标签列表 `{ list: [{ name, count }] }`。字典已落地，此接口大概率废弃 |

> 决策结果确定后：保留 → 后端补实现并移入正式接口章节；删除 → 前端删模块 + 移除本清单。

---

## 数据模型速查（字段 = camelCase）

- `LoginVO`：`token`、`userInfo`(UserVO)
- `UserVO`：`id`(string)、`nickname`、`avatarUrl`(现签 URL)、`avatarKey`(objectKey)、`signature`、`email`、`location`
- `DictItemVO`：`key`、`label`、`sortOrder`、`extra`
- `PresignResp`：`postUrl`、`formData`、`objectKey`、`expiresAt`
- `UploadResp`：`objectKey`、`originalFilename`、`contentType`、`size`
- `RecordVO`：`id`(string)、`title`、`label`、`content`、`images`(访问 URL[])、`recordDate`、`createdAt`、`updatedAt`
- `ReportVO`：`id`(string)、`template`、`title`、`content`、`startDate`、`endDate`、`category`、`recordCount`、`model`、`tokensUsed`

前端类型对应文件：`src/api/types.ts`（手写，无 codegen；后端改字段时同一次改动内同步）。

## 未冻结字段 / 待确认

- `RecordVO` / `ReportVO` 字段**尚未冻结**，后续 AI 生成接入、搜索落地时可能增删字段。
- `/search` 与 `/tags` 是否保留待决策（前端 AI 早期生成，后端未实现，见「过渡接口」）。
- `/report` 为后续迭代预留，前端暂未接入。

## 变更记录

| 日期 | 变更 | 影响 |
|---|---|---|
| 2026-10-06 | 初始清单：对齐两端当前代码（auth/user/dict/file/record/report + 过渡接口） | 建立基线 |
