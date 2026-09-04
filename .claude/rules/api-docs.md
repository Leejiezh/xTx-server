# 外部 API 文档查阅规范

涉及 uniapp 框架前端、微信小程序接口（`wx.*` / `uni.*` API，如 uploadFile、request、chooseMedia、getUserProfile 等）时，**必须先查询一次官方文档**，再写代码或下结论。

- 微信小程序官方文档：https://developers.weixin.qq.com/miniprogram/dev/api/
- uniapp 官方文档：https://uniapp.dcloud.net.cn/api/

重点核实：接口的请求方式（method）、参数、限制（大小/数量/频率）、返回值与错误码。官方文档与模型记忆或推断冲突时，**以官方文档为准**，并在回复中引用文档原文作为依据。

**Why:** 一次会话中曾凭记忆断言 `wx.uploadFile` 只能发 POST，用户质疑「这是官方文档表明的吗」——接口行为必须以官方文档佐证，不能只靠模型记忆。

**How to apply:** 写前端对接代码、排查小程序接口问题、或回答涉及 `wx.*`/`uni.*` 行为的问题时，先 WebFetch / WebSearch 官方文档再行动。若当前环境访问官方文档站点受限（如域名被网络策略拦截），如实告知用户并给出文档链接，让用户自行核对，不要假装查过。
