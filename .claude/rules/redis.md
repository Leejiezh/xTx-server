---
paths:
  - "**/cache/**"
  - "**/*Redis*.java"
  - "**/*Cache*.java"
  - "**/constants/**"
---

# Redis 与缓存

- Redis key 统一由常量类生成，格式 `项目:模块:业务:id`。
- 所有缓存必须设置过期时间，禁止永久 key（确有需要要写明原因）。
- 缓存操作封装在统一的 `RedisUtil` 或 Manager 层，业务层不直接拼 key。
- 注意缓存穿透、击穿、雪崩：热点数据加互斥锁或随机过期时间。
- 分布式锁使用 Redisson 或统一封装，不要手写 `setnx`。
