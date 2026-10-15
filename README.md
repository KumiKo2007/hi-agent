# hi Agent — 智能 Agent 构建平台

> 原名 **AgentX**,现已更名 **hi Agent**。

hi Agent 是一个基于大模型 (LLM) 和多能力平台 (MCP) 的智能 Agent 构建平台。它致力于简化 Agent 的创建流程,让用户无需复杂的流程节点或拖拽操作,仅通过自然语言和工具集成即可打造个性化的智能 Agent。

- **前端**:深色主题 + 极光渐变品牌标识,基于 Next.js 15
- **后端**:Spring Boot (Java 17)
- **开源协议**:MIT

---

## ✨ 核心特性

| 模块 | 说明 |
|------|------|
| **Agent 管理** | 创建、编辑、发布、版本管理、嵌入网站小组件 |
| **大模型服务商** | 多服务商/多模型统一管理,支持 OpenAI 兼容协议 |
| **MCP 工具生态** | 内置工具、工具市场、MCP Server Community、MCP Gateway |
| **RAG 知识库** | 数据集管理、文档切分、语义检索、流式问答 |
| **LLM 上下文管理** | 滑动窗口 + 摘要算法,降低长对话成本 |
| **定时任务** | 为 Agent 配置周期性自动执行任务 |
| **Agent OpenAPI** | 通过 API 密钥对外提供 Agent 能力 |
| **长期记忆** | 跨会话记忆,让 Agent 更了解用户 |
| **执行追踪** | 查看 Agent 运行轨迹、会话详情 |
| **计费系统** | 商品、套餐、账户、订单、支付(支付宝 / Stripe) |
| **容器沙箱** | 通过 Docker 为工具/Agent 提供隔离运行环境 |
| **管理后台** | 用户、工具、Agent、服务商、容器、商品等全量管理 |
| **模型高可用** | API Premium Gateway 高可用网关组件 |

## 🧱 技术栈

| 层 | 技术 |
|----|------|
| 前端 | Next.js 15 · React 19 · TypeScript · Tailwind CSS · shadcn/ui |
| 后端 | Spring Boot 3.2 · Java 17 · MyBatis-Plus · Maven |
| 数据库 | PostgreSQL (pgvector) |
| 消息队列 | RabbitMQ |
| 部署 | Docker / Docker Compose / 独立 jar + node |

## 📁 目录结构

```
.
├── AgentX/                  # Spring Boot 后端 (org.xhy)
│   └── src/main/java/       # 后端源码
├── agentx-frontend-plus/    # Next.js 前端
│   ├── app/                 # 页面路由 (auth / main / widget)
│   ├── components/          # 通用组件 (shadcn/ui)
│   ├── lib/                 # API 与工具库
│   └── styles/              # 全局样式与设计令牌
├── deploy/                  # Docker Compose 部署
│   ├── start.sh             # Linux/macOS 一键启动
│   └── start.bat            # Windows 一键启动
├── docker/                  # 前后端 Dockerfile
├── docs/                    # 文档
└── start-local.bat          # Windows 本地混合启动脚本
```

## 🚀 快速开始

### 方式一:Windows 本地一键脚本(推荐)

需要已安装 **Docker Desktop** 与 **Node.js 18+ / JDK 17**。

```
双击 E:\agentx\AgentX\start-local.bat
```

脚本会自动:
1. 清理可能占用端口的旧发布镜像容器
2. 用 Docker 启动 PostgreSQL + RabbitMQ
3. 启动后端 (`mvnw` 离线模式,http://localhost:8088/api)
4. 启动前端 (http://localhost:3000)

### 方式二:Docker Compose 一键启动

> 适用于本地、内网、服务器环境,会自动从当前源码构建前后端镜像。

```bash
cd deploy
./start.sh        # Linux / macOS
# 或
start.bat         # Windows
```

### 方式三:手动开发模式

**后端**(需本地 PostgreSQL + RabbitMQ,或使用方式一中的 Docker 数据库):

```bash
cd AgentX
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端**:

```bash
cd agentx-frontend-plus
npm install --legacy-peer-deps
npm run dev
```

访问 http://localhost:3000,前端会自动连接 `http://localhost:8088/api`。

## 🔑 默认账号

| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | `admin@agentx.ai` | `admin123` |
| 测试用户 | `test@agentx.ai` | `test123` |

> ⚠️ 生产环境部署前,请务必通过环境变量修改默认密码与 JWT 密钥。

## 🌐 服务端口

| 服务 | 地址 | 说明 |
|------|------|------|
| 前端 | http://localhost:3000 | 主应用 |
| 后端 API | http://localhost:8088/api | 接口服务 |
| PostgreSQL | localhost:5432 | 数据库 |
| RabbitMQ | localhost:5672 | 消息队列 |
| RabbitMQ 管理 | http://localhost:15672 | 队列管理 |
| Adminer | http://localhost:8082 | 数据库管理(开发模式) |
| API 网关 | http://localhost:8081 | 高可用网关(可选) |

## ⚙️ 环境变量

项目使用 `.env` 文件进行配置管理,参考根目录 [.env.example](.env.example)。

**必须修改的配置项**(生产环境):

```env
JWT_SECRET=your_secure_jwt_secret_key_at_least_32_characters
AGENTX_ADMIN_PASSWORD=your_secure_admin_password
DB_PASSWORD=your_secure_db_password
```

**常用配置**:

```env
SERVER_PORT=8088              # 后端端口
DB_HOST=localhost             # 数据库地址
DB_NAME=agentx                # 数据库名
DB_USER=agentx_user           # 数据库用户
RABBITMQ_HOST=localhost       # 消息队列地址
SPRING_PROFILES_ACTIVE=dev    # Spring 环境 (dev / production)
AGENTX_CONTAINER_DOCKER_HOST=npipe:////./pipe/docker_engine   # Windows 下 Docker 连接地址
```

> 完整参数说明参见 `.env.example` 与 [deploy/README.md](deploy/README.md)。

## 🛠 常见问题

**Q: 登录后界面还是旧版?**
检查是否在运行旧的发布镜像容器。删除并改用本地构建:

```bash
docker rm -f agentx
cd deploy && docker compose up -d --build
```

**Q: Docker 构建拉取基础镜像失败(网络超时)?**
配置国内镜像加速(编辑 `~/.docker/daemon.json`):

```json
"registry-mirrors": [
  "https://docker.m.daocloud.io",
  "https://docker.1ms.run",
  "https://dockerproxy.net"
]
```

后端 Docker 构建已内置阿里云 Maven 镜像加速(`docker/backend/settings.xml`)。

**Q: 后端 Maven 依赖下载慢?**
本机已配置依赖缓存时,可用离线模式:`./mvnw -o spring-boot:run`。

## 📖 更多文档

- [对话回归基线与本地测试命令](docs/testing-regression-baseline.md)

| 文档 | 说明 |
|------|------|
| [系统设计文档](docs/agent_design.md) | 系统整体设计 |
| [开发文档](docs/develop_document.md) | 开发指南 |
| [Token 溢出策略](docs/token_overflow_strategy.md) | 上下文管理方案 |
| [执行追踪设计](docs/monitoring/agent-execution-trace-requirements.md) | 监控追踪设计 |
| [新手教程系列](docs/agentx-course/lesson-01-agentx-overview.md) | 从零开始学习本项目 |
| [开发部署指南](deploy/README.md) | 开发者环境配置 |

## 👥 Contributors

- [KumiKo2007](https://github.com/KumiKo2007) — 项目所有者与开发者
- Claude — 本项目 AI 协作开发者(深色主题、品牌设计、代码优化)

## 📄 License

本项目基于 [MIT License](LICENSE) 开源。
