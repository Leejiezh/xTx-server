# file_metadata 表 + 延后清理孤儿文件

直传-后-提交的流程会产生孤儿文件（上传了但未提交表单），非图片下载也需原始文件名。决定：建 `file_metadata` 表（object_key PK、user_id、original_filename、content_type、size、status、record_id、时间戳）跟踪每个上传文件的生命周期；定时清理任务删除 24h 前的 TEMP/DETACHED 行及其 MinIO 对象。这既支持孤儿清理，又保留原始名供下载 Content-Disposition；DETACHED 的 24h 宽限期允许误删恢复。

## Considered options

- 不建表：孤儿永久泄漏，原始名丢失。
- 移除时立即删除：无恢复，需事务保证，复杂度高。
