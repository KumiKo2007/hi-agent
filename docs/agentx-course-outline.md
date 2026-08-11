# AgentX 30 节课课程大纲

这套课程用于循序渐进地学习 AgentX 项目。整体顺序是：先会用，再懂架构，再读核心链路，最后掌握 RAG、MCP、记忆、计费、监控等平台能力。

## 阶段一：项目入门与整体认知

### 第 1 课：课程导学：AgentX 是什么

学习目标：

- 理解 AgentX 的产品定位
- 了解 Agent 平台和普通聊天应用的区别
- 建立后续学习路线

核心内容：

- AgentX 的核心功能
- Agent 创建、对话、工具、知识库、记忆、计费的整体关系
- 本课程的学习方法：围绕“一次对话链路”展开

### 第 2 课：本地环境与项目启动

学习目标：

- 跑通项目
- 理解项目运行依赖

核心内容：

- Docker 部署方式
- 前端、后端、数据库、RabbitMQ 的关系
- `.env` 和 `application.yml` 的作用
- 常见启动问题排查

### 第 3 课：从用户视角体验系统

学习目标：

- 建立产品直觉
- 明白系统主要功能如何被用户使用

核心内容：

- 注册和登录
- 创建 Agent
- 配置模型
- 发起对话
- 配置工具
- 上传知识库
- 查看计费和执行记录

### 第 4 课：整体架构图解读

学习目标：

- 理解系统架构分层
- 知道各模块在运行时的职责

核心内容：

- Frontend Layer
- API Gateway / Backend Layer
- Core Business Services
- Infrastructure Layer
- External Integrations
- 一次请求从前端到模型再返回的完整路径

### 第 5 课：代码目录导览

学习目标：

- 熟悉项目结构
- 知道后续读源码从哪里开始

核心内容：

- `agentx-frontend-plus`
- `AgentX/src/main/java/org/xhy`
- `docs`
- `deploy`
- `docker`
- `db/migration`
- `interfaces / application / domain / infrastructure`

## 阶段二：后端基础与分层架构

### 第 6 课：Spring Boot 后端入口

学习目标：

- 理解后端 API 是如何暴露的

核心内容：

- Controller
- Request DTO
- Response DTO
- 参数校验
- 统一返回结构
- 全局异常处理

### 第 7 课：DDD 分层思想

学习目标：

- 理解项目为什么分成四层
- 能判断代码应该放在哪一层

核心内容：

- `interfaces` 层职责
- `application` 层职责
- `domain` 层职责
- `infrastructure` 层职责
- AppService 和 DomainService 的区别

### 第 8 课：用户、登录与鉴权

学习目标：

- 理解用户体系和权限控制

核心内容：

- 注册登录流程
- JWT 鉴权
- 当前用户上下文
- 管理员和普通用户
- API 权限边界

### 第 9 课：数据库模型与 MyBatis Plus

学习目标：

- 理解数据访问方式
- 能根据实体追踪数据库表

核心内容：

- Entity
- Repository
- MyBatis Plus 查询
- 分页查询
- 软删除
- 数据库迁移脚本

### 第 10 课：配置系统与环境变量

学习目标：

- 理解项目配置如何影响运行

核心内容：

- `.env`
- `application.yml`
- 数据库配置
- RabbitMQ 配置
- LLM 服务商密钥
- 文件存储配置
- 开发环境和生产环境差异

## 阶段三：Agent 核心模型

### 第 11 课：Agent 数据模型

学习目标：

- 理解 Agent 的核心字段和关联关系

核心内容：

- Agent 名称、头像、描述
- 系统提示词
- 欢迎语
- 模型配置
- 工具配置
- 知识库配置
- Agent 和用户的关系

### 第 12 课：Agent 创建与编辑流程

学习目标：

- 跟踪 Agent 创建、更新的完整源码链路

核心内容：

- `PortalAgentController`
- `AgentAppService`
- `AgentDomainService`
- Request 到 DTO 到 Entity 的转换
- 权限校验和业务校验

### 第 13 课：Agent 版本管理

学习目标：

- 理解为什么 Agent 需要版本

核心内容：

- 草稿配置
- 发布版本
- 版本快照
- 版本号
- 回滚
- 更新日志
- AgentEntity 和 AgentVersionEntity 的区别

### 第 14 课：Agent 市场与状态流转

学习目标：

- 理解 Agent 平台化发布流程

核心内容：

- 私有
- 待审核
- 已上架
- 被拒绝
- 已下架
- 市场搜索
- 管理员审核
- 状态流转的权限约束

### 第 15 课：工作区与 Widget

学习目标：

- 理解 Agent 如何被用户复用和嵌入外部页面

核心内容：

- 用户工作区
- 常用 Agent
- Widget 配置
- 嵌入式聊天
- 公开访问入口

## 阶段四：一次对话主链路

### 第 16 课：会话与消息模型

学习目标：

- 理解对话数据如何组织

核心内容：

- Session
- Message
- Context
- 用户消息和模型消息
- 历史消息读取
- 消息入库时机

### 第 17 课：ConversationAppService 主流程

学习目标：

- 读懂一次聊天请求的应用层编排

核心内容：

- `ChatRequest`
- `ChatContext`
- 会话查询
- Agent 查询
- 模型配置加载
- 工具和知识库配置加载
- 流式与同步响应

### 第 18 课：MessageHandler 机制

学习目标：

- 理解不同对话类型如何分发处理

核心内容：

- `MessageHandlerFactory`
- `AbstractMessageHandler`
- `AgentMessageHandler`
- `RagMessageHandler`
- `PreviewMessageHandler`
- 普通对话、RAG 对话、预览对话的区别

### 第 19 课：LLM 调用封装

学习目标：

- 理解模型服务商和模型调用如何被抽象

核心内容：

- Provider
- Model
- ProviderProtocol
- 模型配置
- 高可用模型服务
- LangChain4j 集成
- 调用失败和降级策略

### 第 20 课：SSE 流式响应

学习目标：

- 理解模型输出如何实时返回前端

核心内容：

- SSE 基本概念
- `SseEmitter`
- `SseMessageTransport`
- 同步传输和流式传输
- 前端如何消费流式结果
- 中断对话机制

## 阶段五：Agent 增强能力

### 第 21 课：工具调用基础

学习目标：

- 理解 Agent 如何获得“调用工具”的能力

核心内容：

- 工具市场
- 用户安装工具
- Agent 工具配置
- 工具预设参数
- `ToolProvider`
- 内置工具和外部工具

### 第 22 课：MCP 集成机制

学习目标：

- 理解 MCP 工具如何被后端调用

核心内容：

- MCP Gateway
- MCP Server
- 工具通信协议
- 工具输入输出
- 工具执行结果回传
- Docker 容器管理和工具运行环境

### 第 23 课：RAG 知识库基础

学习目标：

- 理解知识库从文件到向量的处理过程

核心内容：

- 文件上传
- 文件解析
- 文档切分
- 文档片段
- Embedding
- pgvector
- 知识库和 Agent 的绑定

### 第 24 课：RAG 检索流程

学习目标：

- 理解用户问题如何检索出相关知识

核心内容：

- 向量检索
- 关键词检索
- 混合检索
- RRF 融合
- rerank
- query expansion
- 检索结果如何进入 prompt

### 第 25 课：长期记忆机制

学习目标：

- 理解长期记忆和 RAG 的区别

核心内容：

- 记忆候选
- 记忆保存
- hash 去重
- importance
- 记忆向量化
- 用户隔离检索
- 记忆注入对话上下文

## 阶段六：平台工程化能力

### 第 26 课：RabbitMQ 与异步任务

学习目标：

- 理解系统中哪些任务适合异步处理

核心内容：

- RabbitMQ 基础
- 消息发布
- 消息监听
- 文档处理任务
- 定时任务
- 事件驱动和业务解耦

### 第 27 课：计费系统

学习目标：

- 理解 Agent 平台如何做商业化计费

核心内容：

- 账户余额
- 产品
- 订单
- 支付
- token 使用记录
- 模型调用扣费
- 支付网关集成

### 第 28 课：执行追踪与监控

学习目标：

- 理解如何调试一次 Agent 执行过程

核心内容：

- Agent execution trace
- 模型调用记录
- 工具调用记录
- RAG 检索记录
- 错误追踪
- 性能和成本分析

### 第 29 课：前端架构学习

学习目标：

- 理解前端如何组织页面和调用 API

核心内容：

- Next.js App Router
- 页面目录结构
- service 层 API 封装
- hooks
- 聊天组件
- Agent 管理页面
- RAG 管理页面
- 后台管理页面

### 第 30 课：综合实战：新增一个 Agent 能力

学习目标：

- 把前 29 节课的知识串起来

可选实战：

- 新增一个内置工具
- 给 RAG 检索结果展示 score
- 给长期记忆增加开关
- 给 Agent 增加一个配置字段
- 给执行 trace 增加一个记录项

综合练习覆盖：

- 前端表单
- API DTO
- AppService 编排
- DomainService 规则
- 数据库字段
- 对话链路影响
- 测试和验证

## 推荐学习节奏

建议节奏如下：

```text
第 1-5 课：建立项目整体认知
第 6-10 课：掌握后端分层和基础设施
第 11-15 课：深入 Agent 核心模型
第 16-20 课：读懂一次对话主链路
第 21-25 课：学习工具、RAG、记忆等增强能力
第 26-30 课：补齐平台工程化和综合实战
```

学习过程中始终抓住这一条主线：

```text
前端发起请求
  -> 后端组织 Agent 上下文
  -> RAG / 工具 / 记忆增强
  -> 调用模型
  -> 流式返回
  -> 存储、追踪、计费
```

只要这条链路清楚了，AgentX 的其他模块都会变得容易理解。
