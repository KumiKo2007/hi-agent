# AgentX 学习指南

本文档用于帮助你从架构和源码两个角度学习 AgentX。建议不要一开始通读全仓库，而是围绕“一次对话请求如何完成”这条主线逐步展开。

## 1. 项目定位

AgentX 是一个基于大模型和 MCP 工具能力的 Agent 平台，整体可以理解为：

```text
Next.js 前端
  +
Spring Boot 模块化后端
  +
PostgreSQL / pgvector / RabbitMQ / 对象存储 / Docker
  +
外部 LLM / MCP / 支付服务
```

它不是单纯的聊天页面，而是一个完整的 Agent 产品平台，包含：

- Agent 创建、配置、发布和市场审核
- 多模型服务商管理
- 流式对话
- MCP 工具调用
- RAG 知识库检索
- 长期记忆
- 执行追踪
- 计费和支付
- 外部 OpenAPI 和嵌入式 Widget

## 2. 架构分层理解

### 2.1 前端层

目录：

```text
agentx-frontend-plus
```

技术栈：

- Next.js App Router
- React
- Tailwind CSS
- Radix UI

前端主要负责：

- 展示聊天界面
- 管理 Agent
- 配置模型、知识库、工具
- 展示计费、订单、后台管理页面
- 调用后端 API

前端本身不负责核心 Agent 推理逻辑，它更像是“操作台 + 聊天界面 + 管理后台”。

### 2.2 后端入口层

目录：

```text
AgentX/src/main/java/org/xhy/interfaces
```

这一层是 Controller/API 入口，负责：

- 接收 HTTP 请求
- 参数校验
- 用户身份获取
- 调用 application 层服务
- 返回统一响应或 SSE 流式响应

重点目录：

```text
interfaces/api/portal
interfaces/api/admin
interfaces/api/external
interfaces/api/public_api
```

### 2.3 应用服务层

目录：

```text
AgentX/src/main/java/org/xhy/application
```

这一层负责业务流程编排。它通常不会只做单个实体的 CRUD，而是把多个领域服务组合起来完成一个完整用例。

例如一次对话会涉及：

- 会话查询
- Agent 配置查询
- 模型配置查询
- 工具准备
- RAG 上下文准备
- 消息存储
- SSE 返回
- 计费统计

重点类：

```text
application/conversation/service/ConversationAppService.java
application/agent/service/AgentAppService.java
application/rag/service
application/tool/service
application/memory/service
```

### 2.4 领域层

目录：

```text
AgentX/src/main/java/org/xhy/domain
```

这一层承载核心业务规则，比如：

- Agent 状态流转
- Agent 版本管理
- 用户权限判断
- 消息上下文管理
- 工具状态
- RAG 检索策略
- 记忆去重和检索
- 模型服务商管理

重点看这些模块：

```text
domain/agent
domain/conversation
domain/llm
domain/tool
domain/rag
domain/memory
domain/trace
domain/user
```

### 2.5 基础设施层

目录：

```text
AgentX/src/main/java/org/xhy/infrastructure
```

这一层负责和外部系统或技术细节打交道：

- 数据库访问扩展
- LLM 协议适配
- SSE/同步消息传输
- RabbitMQ
- 文件存储
- MyBatis 类型转换
- 全局异常处理
- Docker/MCP/HTTP 客户端等基础能力

## 3. 一次普通对话的主链路

理解 AgentX 最重要的是先读懂一次对话请求。

大致流程如下：

```text
用户在前端发送消息
  ↓
前端调用 Spring Boot API
  ↓
Controller 进入 ConversationAppService
  ↓
根据 sessionId 找到会话和 Agent
  ↓
读取 Agent 配置：prompt、模型、工具、知识库等
  ↓
读取历史消息，组织上下文
  ↓
根据请求类型选择 MessageHandler
  ↓
准备 ToolProvider / RAG 上下文 / 记忆上下文
  ↓
调用 LLM
  ↓
通过 SSE 流式返回前端
  ↓
保存消息、trace、token、计费记录
```

建议优先阅读：

```text
AgentX/src/main/java/org/xhy/application/conversation/service/ConversationAppService.java
AgentX/src/main/java/org/xhy/application/conversation/service/handler/MessageHandlerFactory.java
AgentX/src/main/java/org/xhy/application/conversation/service/message/AbstractMessageHandler.java
AgentX/src/main/java/org/xhy/application/conversation/service/message/agent/AgentMessageHandler.java
AgentX/src/main/java/org/xhy/infrastructure/transport/SseMessageTransport.java
```

读这条链路时重点关注：

- `ChatRequest` 如何变成 `ChatContext`
- `MessageHandlerFactory` 如何选择处理器
- SSE 是如何持续向前端推送内容的
- 消息什么时候保存
- 工具、RAG、记忆是在哪一步进入 prompt 或模型调用的

## 4. Agent 模块学习重点

建议先读：

```text
docs/agent_design.md
AgentX/src/main/java/org/xhy/application/agent/service/AgentAppService.java
AgentX/src/main/java/org/xhy/domain/agent/service/AgentDomainService.java
AgentX/src/main/java/org/xhy/interfaces/api/portal/agent/PortalAgentController.java
```

Agent 模块要重点理解几个概念：

- `AgentEntity`：当前编辑中的 Agent 配置
- `AgentVersionEntity`：已发布版本快照
- 私有、待审核、已上架、被拒绝、已下架等状态
- 工作区 Agent
- Agent 和工具、知识库、模型配置之间的关系

这个模块体现了 Agent 产品化的关键设计：用户不是只发起一次聊天，而是在创建、编辑、发布、复用一个可配置的智能体。

## 5. RAG 模块学习重点

RAG 不是独立系统，而是嵌入在对话链路中的知识增强模块。

重点类：

```text
AgentX/src/main/java/org/xhy/domain/rag/service/HybridSearchDomainService.java
AgentX/src/main/java/org/xhy/domain/rag/service/EmbeddingDomainService.java
AgentX/src/main/java/org/xhy/domain/rag/service/KeywordSearchDomainService.java
AgentX/src/main/java/org/xhy/application/rag/service/search/RAGSearchAppService.java
```

重点理解：

- 文件如何进入知识库
- 文档如何切分成片段
- 文档片段如何向量化
- pgvector 如何保存和检索向量
- 向量检索和关键词检索如何融合
- RRF 融合和 rerank 的作用
- 检索结果如何进入最终 prompt

RAG 对话可以理解为：

```text
用户问题
  ↓
检索相关知识片段
  ↓
把知识片段拼入上下文
  ↓
调用 LLM 生成回答
```

## 6. 工具 / MCP 模块学习重点

工具调用是 Agent 能够“做事”的关键。

重点目录：

```text
AgentX/src/main/java/org/xhy/application/tool
AgentX/src/main/java/org/xhy/domain/tool
AgentX/src/main/java/org/xhy/application/conversation/service/message/agent
```

重点理解：

- Agent 如何配置可用工具
- 工具如何转换成 LangChain4j 的 `ToolProvider`
- 模型如何决定调用工具
- MCP Gateway 如何转发工具调用
- 工具结果如何回到模型上下文

大致流程：

```text
Agent 配置工具
  ↓
后端加载可用工具
  ↓
构造 ToolProvider
  ↓
LLM 发起工具调用
  ↓
MCP Gateway 执行工具
  ↓
工具结果返回给 LLM
  ↓
LLM 生成最终回答
```

## 7. 长期记忆模块学习重点

重点类：

```text
AgentX/src/main/java/org/xhy/domain/memory/service/MemoryDomainService.java
AgentX/src/main/java/org/xhy/application/memory/service/MemoryAppService.java
AgentX/src/main/java/org/xhy/interfaces/api/portal/memory/PortalMemoryController.java
```

重点理解：

- 记忆如何保存
- 如何根据 hash 去重
- 记忆如何向量化
- 如何按用户隔离检索
- 相似度和 importance 如何共同影响排序
- 检索出的记忆如何进入对话上下文

长期记忆和 RAG 很像，但它们的对象不同：

- RAG 面向知识库文档
- Memory 面向用户长期偏好、事实、历史信息

## 8. 计费和监控模块

建议在理解对话主链路后再看。

文档：

```text
docs/billing
docs/monitoring/agent-execution-trace-requirements.md
```

重点理解：

- token 使用量如何记录
- 一次模型调用如何产生 usage record
- 账户余额如何扣减
- trace 如何记录 Agent 执行过程
- 工具调用、模型调用、RAG 检索是否能被追踪

这部分体现的是平台工程化能力。

## 9. 推荐学习顺序

建议按下面顺序学习：

1. 跑通项目，体验创建 Agent 和聊天。
2. 看 `README.md`、`docs/develop_document.md`、`docs/agent_design.md`。
3. 沿一次普通聊天请求读 `ConversationAppService`。
4. 读 `MessageHandlerFactory` 和各种 `MessageHandler`。
5. 读 Agent 创建、编辑、发布和版本管理。
6. 读工具/MCP 注入和调用链路。
7. 读 RAG 检索链路。
8. 读长期记忆链路。
9. 读计费、trace、RabbitMQ、文件处理等工程化模块。
10. 最后再看前端页面如何调用这些 API。

## 10. 推荐练习任务

为了避免只看不懂，建议边学边做几个小任务：

### 任务 1：画一次对话时序图

把下面链路画出来：

```text
Controller
  -> ConversationAppService
  -> MessageHandlerFactory
  -> AgentMessageHandler
  -> LLM Service
  -> SseMessageTransport
  -> MessageDomainService
```

### 任务 2：新增一个简单内置工具

例如新增一个“获取当前时间”的工具，然后让 Agent 可以调用它。

这个任务能帮助你理解：

- 工具如何注册
- 工具如何暴露给模型
- 模型如何触发工具调用
- 工具结果如何返回

### 任务 3：给 RAG 检索结果显示 score

在后端返回 RAG 检索结果时增加 `score` 或 `searchType`，再在前端展示。

这个任务能帮助你理解：

- RAG 检索结果结构
- 前后端 DTO 映射
- 检索质量如何观察

### 任务 4：给长期记忆加开关

给 Agent 或用户设置增加一个“是否启用长期记忆”的开关。

这个任务能帮助你理解：

- 前端配置如何传到后端
- Agent 配置如何影响对话链路
- 记忆检索如何进入上下文

## 11. 学习时的抓手

看这个项目时，始终抓住一条主线：

```text
前端发起请求
  -> 后端组织 Agent 上下文
  -> RAG / 工具 / 记忆增强
  -> 调用模型
  -> 流式返回
  -> 存储、追踪、计费
```

只要这条链路清楚了，其他模块都可以自然挂上去。

这个项目最值得学习的不是“怎么调一次大模型 API”，而是如何把 Agent 做成一个平台：有配置、有版本、有工具、有知识库、有记忆、有监控、有计费、有开放接口。
