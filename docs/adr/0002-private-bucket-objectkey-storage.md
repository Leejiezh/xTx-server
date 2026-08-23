# 私有桶 + DB 存 objectKey + 读时签发 presigned GET

预览图片必须长期有效，但 MinIO 预签名 URL 会过期。决定：桶设为私有；DB 永久存 objectKey（不过期）；读记录时后端按 objectKey 现签发 presigned GET URL 返回前端。如此图片不会因 URL 过期而裂掉，且保留按用户鉴权的能力。

## Considered options

- 公有读桶 + 存永久 URL：无按用户访问控制，记录内容可被遍历，个人记录类应用有隐私风险。
- 私有桶 + DB 存长有效期 presigned URL：仍会过期，只是晚些，最差方案。
