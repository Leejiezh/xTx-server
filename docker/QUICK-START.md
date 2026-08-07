# Docker 快速启动指南（docker目录版）

## 📁 新的目录结构

```
hr-tms-server/
├── docker/                    # ← 所有Docker配置都在这里
│   ├── docker-compose.yml    # ⬅️ 主配置文件
│   ├── mysql/
│   │   ├── conf/
│   │   │   └── my.cnf        # ⬅️ MySQL配置
│   │   └── data/             # 数据（自动创建）
│   └── redis/
│       ├── redis.conf        # ⬅️ Redis配置
│       └── data/             # 数据（自动创建）
├── src/
├── pom.xml
└── README.md
```

## 📝 下载的文件

| 下载文件 | 放置位置 | 新名称 |
|---------|---------|--------|
| `docker-compose-final.yml` | `docker/` | `docker-compose.yml` |
| `my.cnf` | `docker/mysql/conf/` | `my.cnf` |
| `redis-final.conf` | `docker/redis/` | `redis.conf` |

## ⚡ 一键启动（5分钟）

```bash
# 1. 进入项目目录
cd hr-tms-server

# 2. 创建目录结构
mkdir -p docker/mysql/conf docker/redis

# 3. 进入docker目录启动
cd docker
docker compose up -d

# 4. 查看状态（应该看到2个容器运行中）
docker compose ps

# ✅ 完成！
```

## 🚀 日常命令

```bash
# ⭐ 从docker目录启动（推荐）
cd docker
docker compose up -d

# 查看状态
docker compose ps

# 查看日志
docker compose logs -f

# 进入MySQL
docker compose exec mysql8 mysql -uroot -p123456

# 进入Redis
docker compose exec redis-server redis-cli -a yourpassword

# 停止
docker compose down

# 重启
docker compose restart
```

## 📍 两种启动方式

### 方式 A：进入docker目录（推荐）
```bash
cd docker
docker compose up -d
```

### 方式 B：从项目根目录指定配置文件
```bash
docker compose -f docker/docker-compose.yml up -d
```

## ✅ 验证安装成功

```bash
# 进入docker目录
cd docker

# MySQL测试
docker compose exec mysql8 mysql -uroot -p123456 -e "SELECT 1"
# 应该返回 1

# Redis测试  
docker compose exec redis-server redis-cli -a yourpassword ping
# 应该返回 PONG

# 查看容器
docker compose ps
# 应该看到两个容器都在运行
```

## 🔑 连接信息

**MySQL:**
- 容器内: `host: mysql8, port: 3306`
- 本机: `host: localhost, port: 3306`
- 用户: `leejie` / `root`
- 密码: `yourpassword`

**Redis:**
- 容器内: `host: redis-server, port: 6379`
- 本机: `host: localhost, port: 6379`
- 密码: `yourpassword`

## ⚙️ Spring Boot 配置

```properties
# application.properties
spring.datasource.url=jdbc:mysql://mysql8:3306/demo
spring.datasource.username=leejie
spring.datasource.password=123456
spring.redis.host=redis-server
spring.redis.port=6379
spring.redis.password=yourpassword
```

## 🐛 清理旧容器

如果之前有旧的mysql8和redis-server容器：

```bash
# 删除旧容器
docker rm -f mysql8 redis-server

# 然后重新启动
cd docker
docker compose up -d
```

## 📚 详细文档

- **FILE-PLACEMENT-V2.md** 📁 - 详细的文件放置说明
- **DEPLOYMENT-GUIDE.md** 📖 - 完整部署指南和故障排查

## 💾 数据安全

✅ 删除容器不会删除数据：
```bash
docker rm -f mysql8 redis-server
# 数据在 docker/mysql/data/ 和 docker/redis/data/ 中安全保存

# 重新启动时数据会自动恢复
docker compose up -d
```

❌ 只有这样才会删除数据：
```bash
docker compose down -v  # -v 参数会删除所有卷数据
```

## 🎯 完整命令速查

```bash
# 启动
docker compose up -d
docker compose up -d mysql8         # 只启动MySQL
docker compose up -d redis-server   # 只启动Redis

# 停止
docker compose stop
docker compose stop mysql8

# 重启
docker compose restart
docker compose restart mysql8

# 删除容器（保留数据）
docker compose down

# 删除容器和所有数据（谨慎！）
docker compose down -v

# 查看
docker compose ps
docker compose config
docker compose logs -f

# 执行命令
docker compose exec mysql8 bash
docker compose exec redis-server sh
```

## ✨ 可选：创建启动脚本

### docker/start.sh
```bash
#!/bin/bash
cd $(dirname "$0")
docker compose up -d
echo "✅ 服务已启动！"
docker compose ps
```

### docker/stop.sh
```bash
#!/bin/bash
cd $(dirname "$0")
docker compose down
echo "✅ 服务已停止！"
```

使用：
```bash
chmod +x docker/start.sh docker/stop.sh
./docker/start.sh
./docker/stop.sh
```

---

**就这样！现在可以开始了！** 🎉

如果遇到任何问题，参考 DEPLOYMENT-GUIDE.md 中的"故障排查"部分。
