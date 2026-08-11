# 第 5 课：代码目录导览（小白版）

## 这节课要解决什么问题

前面几课我们已经知道：

- AgentX 是一个智能体平台。
- 项目已经能通过 Docker 跑起来。
- 我们体验过前台页面和后台管理页面。
- 我们知道了前端、后端、数据库、大模型、工具之间的大致关系。

这一课开始正式打开代码地图。

但请注意：

```text
这一课不是要你立刻看懂所有代码。
```

这一课只做一件事：

```text
知道每个文件夹大概是干什么的，以后找代码不迷路。
```

学习源码最怕一上来就乱点文件。你会看到很多目录、很多类、很多配置，然后很快迷路。

所以我们先建立一张“项目地图”。

## 本课学习目标

学完本课后，你应该能回答：

1. AgentX 项目根目录在哪里？
2. 前端代码在哪里？
3. 后端代码在哪里？
4. 文档在哪里？
5. Docker 部署文件在哪里？
6. 数据库脚本在哪里？
7. 后端为什么分成 `interfaces`、`application`、`domain`、`infrastructure`？
8. 新手应该先看哪些文件，不应该先看哪些文件？

## 项目根目录

当前项目根目录是：

```text
E:\agentx\AgentX
```

你可以把项目根目录理解成：

```text
这套系统的总文件夹。
```

里面放着：

- 前端代码
- 后端代码
- 文档
- Docker 配置
- 部署脚本
- 数据库脚本
- 课程讲义

## 根目录大概长什么样

项目根目录里常见文件和文件夹大概是：

```text
AgentX
agentx-frontend-plus
docs
deploy
docker
production
README.md
Dockerfile
.env.example
```

先不用理解每个细节。我们一个个讲。

## `AgentX`：后端代码目录

这个目录名字和项目名一样，容易让人有点混乱。

你可以先记住：

```text
E:\agentx\AgentX\AgentX
```

这个目录主要是后端 Java 项目。

后端负责：

- 接收前端请求
- 登录鉴权
- 管理 Agent
- 管理聊天消息
- 调用大模型
- 调用工具
- 检索知识库
- 管理计费
- 保存数据

如果用餐厅比喻：

```text
后端就是餐厅里的服务员、厨房、收银台和管理系统。
```

## 后端最重要的目录

后端源码主要在：

```text
AgentX/src/main/java/org/xhy
```

里面有四个特别重要的目录：

```text
interfaces
application
domain
infrastructure
```

这四个目录是后端学习的核心。

## `interfaces`：后端入口

路径：

```text
AgentX/src/main/java/org/xhy/interfaces
```

你可以把 `interfaces` 理解成：

```text
后端的大门。
```

前端发请求，最先进入的通常就是这里的 Controller。

比如：

- 登录请求
- 获取用户列表
- 创建 Agent
- 发起聊天
- 上传文件
- 后台管理请求

都会先进入某个 Controller。

生活类比：

```text
interfaces 就像餐厅前台。
顾客先到前台点餐，再由前台把需求交给后厨。
```

## `application`：业务流程编排

路径：

```text
AgentX/src/main/java/org/xhy/application
```

你可以把 `application` 理解成：

```text
业务流程调度员。
```

它负责把多个步骤串起来。

比如“一次聊天”不是一步完成的，它可能需要：

1. 找到用户。
2. 找到会话。
3. 找到 Agent。
4. 读取模型配置。
5. 读取历史消息。
6. 准备工具。
7. 准备知识库。
8. 调用模型。
9. 保存消息。
10. 返回结果。

这些步骤的编排，通常就在 `application` 层。

生活类比：

```text
application 像餐厅大堂经理。
它不一定亲自炒菜，但它知道一桌客人的需求要安排哪些人去完成。
```

## `domain`：核心业务规则

路径：

```text
AgentX/src/main/java/org/xhy/domain
```

你可以把 `domain` 理解成：

```text
系统真正的业务规则。
```

比如：

- Agent 有哪些状态？
- Agent 什么情况下可以发布？
- 用户有没有权限操作？
- 消息怎么保存？
- 记忆怎么去重？
- RAG 怎么检索？
- 工具状态怎么变化？

这些规则属于 `domain`。

生活类比：

```text
domain 像餐厅的菜谱、会员规则、收银规则和管理制度。
```

它决定“事情应该怎么做”。

## `infrastructure`：基础设施

路径：

```text
AgentX/src/main/java/org/xhy/infrastructure
```

你可以把 `infrastructure` 理解成：

```text
和底层技术打交道的地方。
```

比如：

- 数据库访问
- RabbitMQ 消息队列
- 文件存储
- 大模型协议适配
- SSE 流式传输
- 全局异常处理
- 配置类

生活类比：

```text
infrastructure 像餐厅里的电、水、燃气、冰箱、仓库、配送系统。
```

用户平时看不到，但系统离不开它。

## 四层关系怎么记

你可以用一句话记住：

```text
interfaces 接请求，
application 排流程，
domain 定规则，
infrastructure 管技术。
```

再用一次聊天举例：

```text
前端发送聊天请求
  ↓
interfaces 接住请求
  ↓
application 安排聊天流程
  ↓
domain 处理会话、消息、Agent 规则
  ↓
infrastructure 调数据库、模型、SSE
```

这就是后端分层的基本思路。

## `agentx-frontend-plus`：前端代码目录

路径：

```text
E:\agentx\AgentX\agentx-frontend-plus
```

这个目录是前端项目。

前端负责：

- 页面显示
- 按钮和表单
- 聊天窗口
- 后台管理界面
- 调用后端 API

生活类比：

```text
前端就是用户看到的菜单、点餐屏、后台管理面板。
```

## 前端里先看哪些目录

前端里比较重要的目录有：

```text
app
components
lib
hooks
types
```

### `app`

页面路由目录。

你在浏览器访问的页面，很多都能在这里找到对应文件。

比如后台：

```text
agentx-frontend-plus/app/(main)/admin
```

### `components`

页面上可复用的组件。

比如：

- 按钮
- 对话框
- 聊天组件
- 表格
- 导航栏

### `lib`

前端调用后端 API 的地方。

比如：

- 用户服务
- Agent 服务
- 后台管理服务
- 工具服务
- 知识库服务

如果你想知道前端某个页面调用了哪个后端接口，经常要看 `lib`。

### `hooks`

React 里的复用逻辑。

新手阶段可以先少看。

### `types`

TypeScript 类型定义。

简单理解：

```text
规定某些数据长什么样。
```

## `docs`：文档目录

路径：

```text
E:\agentx\AgentX\docs
```

这个目录放项目文档。

比如：

- Agent 设计文档
- 开发规范
- 计费系统文档
- RAG 文档
- 监控文档
- SQL 初始化脚本
- 我们生成的课程讲义

你现在的课程目录是：

```text
docs/agentx-course
```

如果你想复习课程，可以从这里看。

## `deploy`：部署目录

路径：

```text
E:\agentx\AgentX\deploy
```

这个目录主要放 Docker Compose 相关部署文件。

比如：

```text
docker-compose.yml
start.bat
start.sh
.env.local.example
.env.production.example
.env.external.example
```

这些文件用于更完整的部署方式。

不过我们现在用的是一体化 Docker 镜像，所以暂时不用深究。

## `docker`：镜像构建目录

路径：

```text
E:\agentx\AgentX\docker
```

里面放前端和后端的 Dockerfile。

Dockerfile 可以理解为：

```text
告诉 Docker 怎么打包这个软件。
```

新手阶段先知道它存在即可，不需要马上看懂。

## `.env.example`：环境变量模板

路径：

```text
E:\agentx\AgentX\.env.example
```

环境变量可以理解成：

```text
系统启动时要用的一些配置。
```

比如：

- 数据库密码
- 管理员账号密码
- JWT 密钥
- 模型服务商配置
- 文件存储配置

我们之前复制生成过：

```text
.env
```

容器启动时会读取 `.env`。

## `README.md`：项目说明书

路径：

```text
E:\agentx\AgentX\README.md
```

README 可以理解成：

```text
项目首页说明书。
```

它通常会告诉你：

- 项目是什么
- 有哪些功能
- 怎么启动
- 默认账号是什么
- 怎么部署
- 有哪些相关链接

学习开源项目时，一般先看 README。

## 数据库脚本在哪里

数据库相关脚本主要在：

```text
AgentX/src/main/resources/db
```

里面有：

```text
migration
rag_tables.sql
```

这些脚本记录数据库表结构。

比如：

- 用户表
- Agent 表
- 消息表
- RAG 表
- 订单表
- 计费表
- trace 表

以后你想知道某个数据到底存在哪里，就要慢慢学会看这些脚本。

## 新手不要一开始看哪些文件

有些文件对新手不友好，建议暂时别急着深挖。

比如：

- 很长的配置文件
- 很复杂的工具调用实现
- 很底层的类型转换类
- 大量数据库 migration 脚本
- Dockerfile 细节
- 前端复杂组件内部状态

不是说它们不重要，而是学习顺序不应该从它们开始。

## 新手应该先看哪些文件

建议先看：

```text
README.md
docs/agentx-course
docs/agent_design.md
docs/develop_document.md
agentx-frontend-plus/app/(main)/admin/page.tsx
AgentX/src/main/java/org/xhy/interfaces
AgentX/src/main/java/org/xhy/application/conversation/service/ConversationAppService.java
```

其中 `ConversationAppService` 现在不用看懂，只要知道：

```text
它是后面学习一次对话主链路的重要入口。
```

## 怎么用页面找代码

以后我们会经常用这个方法：

```text
先看页面地址
  ↓
再找前端页面文件
  ↓
再找前端调用的 API
  ↓
再找后端 Controller
  ↓
再找 AppService
  ↓
再找 DomainService
```

比如后台用户管理页面：

```text
http://localhost:3000/admin/users
```

我们可以猜：

前端页面大概在：

```text
agentx-frontend-plus/app/(main)/admin/users/page.tsx
```

后端接口大概在：

```text
interfaces/api/admin/user
```

业务逻辑大概在：

```text
application/user
domain/user
```

这就是通过页面反推代码。

## 本课小结

这一课最重要的是建立代码地图。

你可以先记住：

```text
agentx-frontend-plus 是前端。
AgentX 是后端。
docs 是文档。
deploy 是部署。
docker 是镜像构建。
.env 是运行配置。
```

后端四层：

```text
interfaces 接请求。
application 排流程。
domain 定规则。
infrastructure 管技术。
```

以后学习源码，不要到处乱点文件。

先问自己：

```text
我现在想看的是页面、接口、业务流程、业务规则，还是底层技术？
```

然后再去对应目录找。

## 课后练习

### 练习 1：找到项目根目录

打开文件资源管理器，进入：

```text
E:\agentx\AgentX
```

观察有哪些文件夹。

### 练习 2：找到前端目录

找到：

```text
agentx-frontend-plus
```

看看里面有没有：

```text
app
components
lib
```

### 练习 3：找到后端目录

找到：

```text
AgentX/src/main/java/org/xhy
```

看看里面有没有：

```text
interfaces
application
domain
infrastructure
```

### 练习 4：用一句话解释四层

补全下面句子：

```text
interfaces 负责：
application 负责：
domain 负责：
infrastructure 负责：
```

建议答案：

```text
interfaces 负责接请求。
application 负责安排流程。
domain 负责业务规则。
infrastructure 负责底层技术。
```

### 练习 5：通过页面找文件

打开后台用户管理：

```text
http://localhost:3000/admin/users
```

然后在项目里找到：

```text
agentx-frontend-plus/app/(main)/admin/users/page.tsx
```

先不用看懂代码，只要知道页面文件在哪里。

## 下一课预告

第 6 课会开始讲 Spring Boot 后端入口。

下一课会解释：

- 什么是 Controller
- 什么是 API
- 前端请求怎么进入后端
- `@RestController` 是什么
- `Request` 和 `Response` 是什么
- 为什么后端要做参数校验和异常处理

从第 6 课开始，我们会真正进入后端代码。
