# Record.images 存 JSON objectKey 数组（保留列）

Record.images 列已存在（String，注释"图片URL数组"），一条记录有有序图片列表。决定：保留该列，存 objectKey 的 JSON 数组，经 MyBatis-Plus 类型转换器映射为 Java 的 `List<String>`；文件生命周期（status/record_id）单独由 file_metadata 跟踪供清理任务使用。最小改动现有 schema，记录的有序图片列表与记录同处一行；file_metadata.object_key 引用同一批 key（引用非冗余）。列名注释说"URL数组"但实际存 objectKey——读时由 VO 转为 access URL。

## Considered options

- 删 Record.images，关系完全靠 file_metadata（record_id + sort_order）：更规范化，但需删现有列、改 entity/DTO/VO、每次读都 join。
