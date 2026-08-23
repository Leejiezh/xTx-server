# 小兔鲜 xTx-server

记录与报告类个人应用的后端服务，集成 MinIO 文件存储。本文件是项目领域术语表，仅定义本上下文特有的概念。

## 文件

**objectKey（对象键）**:
文件在对象存储中的稳定路径式标识（如 `img/1024/2026/08/23/a1b2c3d4.jpg`）。永久存于数据库，永不过期。
_避免_：URL、文件链接

**Image（图片）**:
content-type 为 `image/*` 的文件。前端通过预签名 PUT 直传 MinIO（不经后端字节），内联预览。
_避免_：picture、photo

**Document（文档）**:
非图片文件（pdf、docx 等）。经后端代理上传（后端接收字节、校验、存入 MinIO），下载时带原始文件名。
_避免_：file、attachment（过于宽泛）

**orphan（孤儿文件）**:
无活动引用的已存储文件——上传了但表单未提交，或被编辑从记录中移除。过宽限期后可被清理。
_避免_：leaked file、dangling file

## 文件生命周期

每个文件与记录的关系状态：

**TEMP（临时）**:
已上传但未附加到任何记录（或永不会）。24h 后可被清理。
_避免_：pending、unattached

**ATTACHED（已附加）**:
已链接到记录（record_id 已设）。受清理任务保护，不被清理。
_避免_：active、bound

**DETACHED（已分离）**:
曾附加到记录，后被编辑移除。24h 后可被清理——宽限期允许误删恢复。
_避免_：deleted（尚未删除）、unlinked

## 访问

**access URL（访问 URL）**:
后端读时签发的短期预签名 URL，返回前端用于预览（`inline`）或下载（`attachment; filename=原始名`）。区别于 objectKey——URL 会过期，objectKey 不会。
_避免_：URL（歧义——需指明 objectKey 还是 access URL）
