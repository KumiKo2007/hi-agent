# 第 2 课：本地环境与项目启动

## 课程定位

第 1 课我们从整体上认识了 AgentX：它不是一个简单聊天页面，而是一个包含 Agent 管理、对话运行时、RAG、MCP 工具、长期记忆、计费和监控的平台。

第 2 课开始进入实践：把项目跑起来。

这一课的目标不是马上修改代码，而是先理解 AgentX 运行时需要哪些服务、各个服务之间如何连接，以及启动失败时应该从哪里排查。

## 本课学习目标

学完本课后，你应该能够回答这些问题：

1. AgentX 本地运行需要哪些基础服务？
2. 一键 Docker 部署和源码开发启动有什么区别？
3. 前端、后端、数据库、RabbitMQ 分别使用哪些端口？
4. `.env`、`application.yml`、`docker-compose.yml` 分别负责什么？
5. 如果项目启动失败，应该按什么顺序排查？

## 先建立运行时图景

AgentX 跑起来以后，大致由这些部分组成：

```text
浏览器
  ↓
Next.js 前端，端口 3000
  ↓
Spring Boot 后端，端口 8088
  ↓
PostgreSQL + pgvector，端口 5432
RabbitMQ，端口 5672 / 15672
对象存储或本地存储
外部 LLM Provider
MCP Gateway 或工具服务
支付网关
```

其中，最核心的是：

- 前端：给用户操作界面
- 后端：承载所有业务逻辑
- PostgreSQL：保存业务数据
- pgvector：保存向量数据
- RabbitMQ：处理异步消息
- LLM Provider：真正产生模型回复

## 项目目录回顾

项目根目录是：

```text
E:\agentx\AgentX
```

本课重点关注这些文件和目录：

```text
README.md
.env.example
Dockerfile
deploy/
deploy/docker-compose.yml
deploy/start.bat
deploy/start.sh
deploy/.env.local.example
deploy/.env.external.example
deploy/.env.production.example
AgentX/pom.xml
AgentX/src/main/resources/application.yml
agentx-frontend-plus/package.json
```

它们分别对应：

- 根目录 README：项目整体启动说明
- `.env.example`：一键部署的环境变量模板
- `Dockerfile`：构建完整镜像
- `deploy/docker-compose.yml`：开发环境多服务编排
- `deploy/start.bat` / `start.sh`：开发环境启动脚本
- `AgentX/pom.xml`：后端 Java 依赖
- `application.yml`：Spring Boot 配置
- `package.json`：前端 Next.js 依赖和启动脚本

## 两种启动方式

AgentX 通常有两种启动方式。

### 方式一：一键 Docker 体验

适合目标：

- 快速体验完整功能
- 不准备马上改代码
- 想先看产品效果

这种方式通常会把前端、后端、数据库、RabbitMQ 等打包在一起运行。

典型访问地址：

```text
前端页面：http://localhost:3000
后端 API：http://localhost:8088
PostgreSQL：localhost:5432
RabbitMQ：localhost:5672
RabbitMQ 管理页面：http://localhost:15672
```

优点：

- 启动简单
- 依赖集中
- 适合快速体验

缺点：

- 不适合边改源码边调试
- 排查问题时不如源码启动直观

### 方式二：源码开发启动

适合目标：

- 学习源码
- 修改后端 Java 代码
- 修改前端页面
- 调试数据库和消息队列
- 做课程后续实战任务

源码开发通常会把服务拆开：

```text
PostgreSQL / RabbitMQ 通过 Docker 启动
后端用 IDE 或 Maven 启动
前端用 pnpm/npm/yarn 启动
```

优点：

- 适合调试
- 修改代码后反馈快
- 能看清各个服务之间的关系

缺点：

- 环境配置更复杂
- 更容易遇到端口、依赖、环境变量问题

学习源码时，推荐最终采用源码开发启动。

## 需要安装的软件

建议准备以下环境。

### 1. Docker Desktop

用于启动：

- PostgreSQL
- RabbitMQ
- 一键完整部署
- 可能的 MCP 工具容器

检查命令：

```powershell
docker --version
docker compose version
```

### 2. Java 17

后端基于 Spring Boot 3，项目 `pom.xml` 中配置的是 Java 17。

检查命令：

```powershell
java -version
```

你应该看到类似：

```text
version "17.x"
```

### 3. Maven

后端使用 Maven 管理依赖。

检查命令：

```powershell
mvn -version
```

### 4. Node.js

前端使用 Next.js，建议使用较新的 Node.js LTS 版本。

检查命令：

```powershell
node -v
npm -v
```

### 5. IDE

推荐：

- IntelliJ IDEA：后端 Java 调试
- VS Code：前端和 Markdown 文档编辑

## 环境变量文件

AgentX 中有多类配置文件，需要先分清。

### `.env`

通常用于 Docker 启动时注入环境变量。

你可以从模板复制：

```powershell
Copy-Item .env.example .env
```

然后按需修改：

- 数据库密码
- RabbitMQ 密码
- JWT 密钥
- 管理员密码
- 模型服务商配置
- 文件存储配置

### `deploy/.env.local.example`

适合本地开发环境。

可以复制成：

```powershell
Copy-Item deploy\.env.local.example deploy\.env.local
```

### `deploy/.env.external.example`

适合使用外部数据库、外部 RabbitMQ 等场景。

### `deploy/.env.production.example`

适合生产部署参考。

## 关键端口说明

建议把这些端口记下来：

| 服务 | 默认端口 | 作用 |
| --- | --- | --- |
| 前端 Next.js | 3000 | 浏览器访问入口 |
| 后端 Spring Boot | 8088 | API 服务 |
| PostgreSQL | 5432 | 业务数据和向量数据 |
| RabbitMQ | 5672 | 消息队列协议端口 |
| RabbitMQ 管理后台 | 15672 | 可视化管理页面 |
| API Premium Gateway | 8081 | 可选的模型高可用网关 |

如果启动失败，经常是这些端口被占用。

Windows 上可以用：

```powershell
netstat -ano | findstr :3000
netstat -ano | findstr :8088
netstat -ano | findstr :5432
netstat -ano | findstr :5672
```

## 一键 Docker 体验流程

如果你只是想先体验完整系统，可以按这个思路：

### 第一步：准备 `.env`

在项目根目录：

```powershell
Copy-Item .env.example .env
```

然后编辑 `.env`。

最少要关注：

```text
JWT_SECRET
AGENTX_ADMIN_PASSWORD
DB_PASSWORD
RABBITMQ_PASSWORD
```

如果要真实调用模型，还需要配置模型服务商相关 API Key。

### 第二步：启动容器

项目 README 中提供了一键运行镜像的方式。学习阶段更重要的是理解它做了什么：

```text
启动一个 agentx 容器
映射前端 3000
映射后端 8088
映射数据库 5432
映射 RabbitMQ 5672 和 15672
挂载数据库数据卷
挂载文件存储目录
可能挂载 Docker socket 以管理工具容器
```

### 第三步：访问系统

浏览器打开：

```text
http://localhost:3000
```

后端健康检查可以尝试：

```text
http://localhost:8088
```

具体健康检查路径以后端 Controller 配置为准。

RabbitMQ 管理页面：

```text
http://localhost:15672
```

## 源码开发启动流程

源码开发更适合后续课程。

### 第一步：启动基础设施

通常先启动：

- PostgreSQL
- RabbitMQ

可以通过 `deploy/docker-compose.yml` 启动。

进入部署目录：

```powershell
cd E:\agentx\AgentX\deploy
```

启动：

```powershell
docker compose up -d
```

查看状态：

```powershell
docker compose ps
```

查看日志：

```powershell
docker compose logs -f
```

### 第二步：启动后端

进入后端目录：

```powershell
cd E:\agentx\AgentX\AgentX
```

可以用 Maven：

```powershell
mvn spring-boot:run
```

也可以用 IntelliJ IDEA 打开后端模块，找到 Spring Boot 启动类后运行。

后端启动成功后，应监听：

```text
http://localhost:8088
```

### 第三步：启动前端

进入前端目录：

```powershell
cd E:\agentx\AgentX\agentx-frontend-plus
```

安装依赖：

```powershell
npm install
```

启动开发服务：

```powershell
npm run dev
```

前端启动成功后访问：

```text
http://localhost:3000
```

## 前端如何找到后端

前端需要知道 API 地址。常见方式有两种：

1. 通过环境变量指定后端地址
2. 前端代码根据当前访问地址自动推断后端地址

在学习时，你要关注前端这些文件：

```text
agentx-frontend-plus/lib/api-config.ts
agentx-frontend-plus/lib/http-client.ts
agentx-frontend-plus/lib/api.ts
```

它们通常负责：

- 拼接 API base URL
- 注入 token
- 统一处理响应
- 处理错误

后面学习前端架构时会详细展开。

## 后端如何连接数据库和 RabbitMQ

后端配置主要在：

```text
AgentX/src/main/resources/application.yml
```

重点关注：

```text
spring.datasource
spring.rabbitmq
server.port
```

同时，Docker 或启动脚本会通过环境变量覆盖部分配置。

这意味着：当你发现后端连不上数据库时，不要只看 `application.yml`，还要看 `.env`、Docker Compose、启动脚本中的环境变量。

## 数据库迁移

数据库脚本在：

```text
AgentX/src/main/resources/db/migration
```

这些脚本记录了项目表结构的演进，例如：

- 用户表字段
- 容器表
- RAG 发布表
- 计费表
- 订单支付表
- Agent 执行 trace 表
- 消息 token 字段
- Widget 表

学习源码时，建议经常把 Entity 和 migration 脚本对照起来看。

## 启动后的验证清单

启动项目后，按下面顺序验证。

### 1. 容器是否正常

```powershell
docker ps
```

看 PostgreSQL、RabbitMQ 等容器是否启动。

### 2. 后端是否正常

检查后端日志，确认没有：

- 数据库连接失败
- RabbitMQ 连接失败
- 端口被占用
- 配置缺失
- Bean 初始化失败

### 3. 前端是否正常

访问：

```text
http://localhost:3000
```

确认页面能打开。

### 4. 前端是否能调通后端

打开浏览器开发者工具，看 Network：

- API 是否请求到 `8088`
- 是否有 CORS 错误
- 是否有 401 未登录
- 是否有 500 后端异常

### 5. 登录是否正常

如果项目内置了默认账号，可以尝试登录。

如果无法登录，优先看：

- 用户数据是否初始化
- 数据库连接是否正确
- 密码配置是否和 `.env` 一致

## 常见问题排查

### 问题 1：端口被占用

现象：

```text
Port 3000 already in use
Port 8088 already in use
```

排查：

```powershell
netstat -ano | findstr :3000
netstat -ano | findstr :8088
```

解决思路：

- 关闭占用端口的进程
- 或修改服务端口

### 问题 2：后端连不上数据库

常见原因：

- PostgreSQL 容器没启动
- 数据库端口没映射
- 用户名或密码错误
- `.env` 和 `application.yml` 不一致
- 数据库还在初始化

排查顺序：

```text
docker ps
  -> docker logs
  -> application.yml
  -> .env
  -> 后端启动日志
```

### 问题 3：RabbitMQ 连接失败

常见原因：

- RabbitMQ 容器没启动
- 端口 5672 没开放
- 用户名密码错误
- 后端配置和 Docker 配置不一致

可以访问：

```text
http://localhost:15672
```

确认 RabbitMQ 管理页面是否能打开。

### 问题 4：前端页面能打开，但 API 报错

常见原因：

- 后端没启动
- API base URL 配错
- CORS 配置问题
- 登录 token 缺失
- 后端返回 500

排查重点：

- 浏览器 Network 面板
- 后端日志
- `agentx-frontend-plus/lib` 下的 API 配置文件

### 问题 5：模型无法回复

常见原因：

- 没配置模型服务商
- API Key 错误
- Endpoint 错误
- 模型名不支持
- 网络无法访问外部模型服务
- 余额不足或模型服务商返回限流

排查重点：

- 模型服务商配置页面
- 后端 LLM 调用日志
- API Key 是否生效
- 当前 Agent 是否绑定了有效模型

## 本课建议实际操作

如果你要真正学习这个项目，建议完成下面操作。

### 操作 1：确认基础软件

执行：

```powershell
docker --version
java -version
mvn -version
node -v
npm -v
```

把输出记录到自己的学习笔记中。

### 操作 2：浏览部署文件

打开：

```text
deploy/docker-compose.yml
deploy/start.bat
deploy/.env.local.example
```

重点看：

- 启动了哪些服务
- 映射了哪些端口
- 使用了哪些环境变量
- 数据卷挂载在哪里

### 操作 3：浏览后端配置

打开：

```text
AgentX/src/main/resources/application.yml
```

重点看：

- `server.port`
- `spring.datasource`
- `spring.rabbitmq`
- 文件存储配置
- 其他第三方服务配置

### 操作 4：浏览前端启动脚本

打开：

```text
agentx-frontend-plus/package.json
```

重点看：

```json
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "start": "next start"
}
```

理解开发启动和生产启动的区别。

## 本课小结

本课你需要记住的是：

AgentX 不是单进程应用，而是一组服务协同运行：

```text
前端负责界面
后端负责业务
数据库负责持久化
pgvector 负责向量检索
RabbitMQ 负责异步任务
LLM Provider 负责生成回复
MCP / Docker 负责工具能力
```

学习源码前，先把这些运行依赖搞清楚，会让后面的代码阅读轻松很多。

尤其要建立一个排查习惯：

```text
页面有问题，看浏览器 Network
API 有问题，看后端日志
数据有问题，看数据库和 migration
异步任务有问题，看 RabbitMQ
模型回复有问题，看模型配置和 LLM 日志
```

## 课后练习

### 练习 1：画出本地运行架构图

画出下面服务之间的关系：

```text
Browser
Next.js Frontend
Spring Boot Backend
PostgreSQL
RabbitMQ
LLM Provider
MCP Gateway
```

### 练习 2：列出关键端口

写出下面服务的默认端口：

- 前端
- 后端
- PostgreSQL
- RabbitMQ
- RabbitMQ 管理后台
- API Premium Gateway

### 练习 3：阅读 docker-compose

打开 `deploy/docker-compose.yml`，回答：

1. 启动了哪些容器？
2. 哪些端口映射到了宿主机？
3. 哪些数据被挂载成 volume？
4. 后端依赖哪些环境变量？

### 练习 4：阅读 application.yml

打开 `AgentX/src/main/resources/application.yml`，回答：

1. 后端默认端口是多少？
2. 数据库配置在哪里？
3. RabbitMQ 配置在哪里？
4. 哪些配置可能会被环境变量覆盖？

### 练习 5：整理启动问题清单

为自己准备一个表格：

| 问题现象 | 可能原因 | 排查位置 |
| --- | --- | --- |
| 前端打不开 | 前端没启动或端口占用 | npm 日志 / 端口 |
| API 失败 | 后端没启动或地址错误 | Network / 后端日志 |
| 数据库失败 | PostgreSQL 未启动或密码错误 | docker logs / application.yml |
| 模型无回复 | API Key 或模型配置错误 | 后端 LLM 日志 |

## 下一课预告

第 3 课会从用户视角体验系统。

下一课重点是：

- 登录系统
- 创建第一个 Agent
- 配置模型
- 发起一次对话
- 观察前端请求和后端响应
- 用产品流程反推源码模块

从第 3 课开始，我们会把“能跑起来”变成“知道每个功能背后对应哪块代码”。
