# 分页接口契约（统一请求参数 / 响应格式）

> 供前端对齐。本文档只描述**稳定的通用契约**（响应外壳、分页参数、分页响应结构）。
> 各业务 VO 的字段清单见文末，**字段暂未冻结**，会随前端需求调整，请以最终接口为准。

- 基础路径：`/api`（xtx-api 端口 8080）
- 参数命名：**camelCase**，请求参数名与响应字段名一致，前端无需做名字映射。

---

## 1. 统一响应外壳 `R<T>`

所有接口（含分页）外层都是它，字段固定 `code / msg / data`：

```jsonc
{
  "code": 200,        // int    业务码。200=成功；其他=失败
  "msg": "success",   // string 提示文案，成功时为 "success"
  "data": { }         // 泛型，分页接口里就是 PageResult（见第 3 节）
}
```

失败码约定：

| code | 含义 |
|---|---|
| `200` | 成功 |
| `400` | 参数异常 |
| `401` | 未登录或登录已过期 |
| `403` | 无权访问 |
| `422` | 微信业务错误 |
| `500` | 通用/系统错误 |

**判断依据是 body 里的 `code`，不是 HTTP 状态码。** 业务异常仍以 **HTTP 200** 返回，只有 Security 的 401/403 会同步设置 HTTP 状态码。前端一律读 `body.code`。

---

## 2. 分页请求参数 `PageQuery`

`GET` 查询串传参（**不是 JSON body**），所有分页接口通用：

| 参数 | 类型 | 必填 | 默认 | 约束 | 说明 |
|---|---|---|---|---|---|
| `pageNum` | int | 否 | `1` | ≥ 1 | 页码，从 1 开始 |
| `pageSize` | int | 否 | `10` | 1 ~ 100 | 每页条数 |

示例：`GET /api/record/page?pageNum=1&pageSize=10`

- 不传或传空串，后端都会兜成默认值 `1` / `10`。
- `pageSize` 声明上限 100，前端不要超过。

---

## 3. 分页响应 `PageResult<V>`

`data` 的固定结构，`list` 里放具体业务 VO：

```jsonc
{
  "list": [ /* 当前页数据，V 类型见第 4 节 */ ],
  "total": 42,        // long  总条数（"共 N 条"文案用）
  "pageNum": 1,       // long  当前页码（与请求同名）
  "pageSize": 10,     // long  每页条数（与请求同名）
  "hasNext": true     // bool  是否还有下一页
}
```

**上拉加载约定**：前端只需 `if (hasNext) pageNum++` 再请求，不用自己算总页数。`list` 为空时返回 `[]`，不会是 `null`。

---

## 4. 现有分页接口

| 接口 | 方法 | 返回 `data` |
|---|---|---|
| `/api/record/page` | GET | `PageResult<RecordVO>` |
| `/api/report/page` | GET | `PageResult<ReportVO>` |

> 目前这两个接口**只接受分页参数本身**，没有额外筛选字段。后续若加筛选，会以同名查询参数追加。

---

## 5. 业务 VO 字段（⚠️ 暂未冻结，可能随前端调整）

### 5.1 `RecordVO`（记录）

```jsonc
{
  "id": 1892345678901234567,  // Long  雪花ID —— 超出 JS 安全整数范围，前端须按字符串处理
  "category": "LIFE",          // "LIFE" | "STUDY"
  "content": "今天去了公园",
  "images": ["https://minio.../presigned-url"], // string[] 预签名访问URL，可能为空数组
  "recordDate": "2026-10-03",  // "yyyy-MM-dd"
  "source": "MANUAL"           // "MANUAL" | "IMAGE"
}
```

### 5.2 `ReportVO`（报告）

```jsonc
{
  "id": 1892345678901234568,   // Long 雪花ID
  "template": "DIARY",          // "DIARY" | "WEEKLY" | "STUDY_SUMMARY" | "REVIEW"
  "title": "本周学习总结",
  "content": "## Markdown 正文...",
  "startDate": "2026-09-28",    // "yyyy-MM-dd"
  "endDate": "2026-10-03",      // "yyyy-MM-dd"
  "category": "ALL",            // "LIFE" | "STUDY" | "ALL"
  "recordCount": 12,            // 基于多少条记录生成
  "model": "qwen-plus",
  "tokensUsed": 1523
}
```

---

## 6. 完整响应示例

`GET /api/record/page?pageNum=1&pageSize=2`

```jsonc
{
  "code": 200,
  "msg": "success",
  "data": {
    "list": [
      { "id": 1892345678901234567, "category": "LIFE",  "content": "今天去了公园", "images": [], "recordDate": "2026-10-03", "source": "MANUAL" },
      { "id": 1892345678901234566, "category": "STUDY", "content": "复习了英语",   "images": ["https://minio.../a.jpg"], "recordDate": "2026-10-02", "source": "IMAGE" }
    ],
    "total": 42,
    "pageNum": 1,
    "pageSize": 2,
    "hasNext": true
  }
}
```

---

## 7. TypeScript 类型参考

```ts
// 统一响应
interface R<T> { code: number; msg: string; data: T }

// 分页请求参数
interface PageQuery { pageNum?: number; pageSize?: number }

// 分页响应
interface PageResult<V> {
  list: V[];
  total: number;
  pageNum: number;
  pageSize: number;
  hasNext: boolean;
}

// ⚠️ 以下 VO 字段暂未冻结，以最终接口为准
interface RecordVO {
  id: string;            // 雪花ID，按字符串接收，避免精度丢失
  category: 'LIFE' | 'STUDY';
  content: string;
  images: string[];
  recordDate: string;    // yyyy-MM-dd
  source: 'MANUAL' | 'IMAGE';
}

interface ReportVO {
  id: string;
  template: 'DIARY' | 'WEEKLY' | 'STUDY_SUMMARY' | 'REVIEW';
  title: string;
  content: string;
  startDate: string;
  endDate: string;
  category: 'LIFE' | 'STUDY' | 'ALL';
  recordCount: number;
  model: string;
  tokensUsed: number;
}

type RecordPageResp = R<PageResult<RecordVO>>;
type ReportPageResp = R<PageResult<ReportVO>>;
```

---

## 8. 前端对接要点

1. **`id` 是雪花 ID（Long）**，值远超 JS `Number.MAX_SAFE_INTEGER`，务必按**字符串**处理，否则末尾几位会丢精度。
2. **失败判断看 `body.code`，不看 HTTP 状态码**——业务异常后端返回 HTTP 200 + `code != 200`。
3. **分页只有 `pageNum` / `pageSize` 两个参数，上拉加载靠 `hasNext`**，请求/响应字段同名，无需映射。
