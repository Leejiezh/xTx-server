# 混合上传路径：图片预签名直传 + 文档后端代理

文件需上传至 MinIO。图片多而小、是省后端带宽的主要场景；非图片文档较少较大、需后端校验并保留原始文件名用于下载。决定：`image/*` 走 S3 POST 表单直传（后端 `presign` 返回 `postUrl`+`formData`，前端 `uni.uploadFile` 直传 MinIO，不经后端字节；`uni.uploadFile` 只能发 multipart POST、发不了 PUT，故用 POST 表单而非 PUT 预签名 URL）；非图片走后端代理上传。如此在常见场景（图片）省后端带宽，同时保留对文档的后端控制（校验、原始名 Content-Disposition）。

## Considered options

- 全部前端直传：失去对文档的校验与原始名保留。
- 全部后端代理：图片场景后端成为带宽瓶颈。
- 图片走 PUT 预签名 URL：小程序 `wx.uploadFile`/`uni.uploadFile` 发不了 PUT，需要 HTTP 客户端手动发 PUT 才能直传，与 uni 框架冲突——故弃用，改 POST 表单。
