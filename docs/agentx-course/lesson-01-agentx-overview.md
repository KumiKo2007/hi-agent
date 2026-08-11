# 第 1 课：课程导学：AgentX 是什么

## 课程定位

这一课是整套 AgentX 源码课程的入口。目标不是马上深入代码细节，而是先建立一个清晰的整体认知：

- AgentX 解决什么问题
- 它和普通聊天应用有什么区别
- 它有哪些核心模块
- 后续 30 节课应该按照什么顺序学习
- 阅读源码时应该抓住哪条主线

如果一开始就直接看 Controller、Service、Entity，很容易陷入大量细节。学习 AgentX 更好的方式，是先把它看成一个完整的 Agent 产品平台，然后再沿着“一次对话请求”逐层拆解。

## 本课学习目标

学完本课后，你应该能够回答这些问题：

1. AgentX 是一个什么类型的项目？
2. AgentX 和普通 ChatGPT 套壳应用有什么不同？
3. AgentX 的核心功能模块有哪些？
4. 为什么“一次对话链路”是学习这个项目的主线？
5. 后续应该先读哪些目录和文档？

## 先修知识

学习本项目不要求你一开始掌握所有技术，但建议具备以下基础：

- Java 基础语法
- Spring Boot 基础概念
- HTTP API 基础
- React 或 Next.js 基础认知
- 数据库基础
- 对大模型 API 有基本了解

如果你暂时不熟悉 DDD、RAG、MCP、SSE、RabbitMQ，也没关系。后续课程会逐步拆开讲。

## 一句话理解 AgentX

AgentX 是一个面向 Agent 构建和运营的开源平台。

它不只是一个聊天页面，而是把 Agent 创建、模型配置、工具调用、知识库、长期记忆、计费、监控、开放接口等能力整合在一起。

可以用下面这句话概括：

```text
AgentX = Agent 管理平台 + 对话运行时 + 工具/MCP 系统 + RAG 知识库 + 记忆系统 + 计费和监控
```

## AgentX 和普通聊天应用的区别

普通聊天应用通常只做这几件事：

```text
用户输入问题
  -> 调用大模型 API
  -> 返回回答
```

AgentX 要复杂得多：

```text
用户输入问题
  -> 找到当前会话
  -> 找到当前 Agent
  -> 加载 Agent 的系统提示词
  -> 加载模型配置
  -> 加载历史上下文
  -> 检索知识库
  -> 检索长期记忆
  -> 准备可调用工具
  -> 调用大模型
  -> 可能触发工具调用
  -> 汇总工具结果
  -> 流式返回回答
  -> 保存消息
  -> 记录 trace
  -> 统计 token
  -> 执行计费
```

也就是说，普通聊天应用的核心是“调用模型”，AgentX 的核心是“组织一次完整的 Agent 执行过程”。

## 核心产品能力

从产品角度看，AgentX 至少包含以下能力。

### 1. Agent 管理

用户可以创建自己的 Agent，并配置：

- 名称
- 头像
- 描述
- 系统提示词
- 欢迎语
- 模型
- 工具
- 知识库
- 记忆能力

这部分对应的是 AgentX 的核心模型。

### 2. Agent 版本和市场

Agent 不只是临时配置，还可以发布成版本，并进入市场流转。

典型状态包括：

- 私有
- 待审核
- 已上架
- 被拒绝
- 已下架

这让 Agent 从“个人配置”变成了“可发布、可复用、可审核的产品”。

### 3. 多模型服务商

AgentX 支持不同的大模型服务商和模型配置。

它需要管理：

- Provider
- Model
- API Key
- Endpoint
- 模型状态
- 官方模型和用户自定义模型

这部分决定了 Agent 最终调用哪个模型。

### 4. 对话系统

对话系统负责：

- 创建会话
- 保存用户消息
- 保存模型回复
- 管理上下文
- 支持流式输出
- 支持中断对话

后续学习源码时，对话系统是第一条必须读懂的主线。

### 5. 工具和 MCP

Agent 的关键能力之一是调用工具。

例如：

- 查询外部数据
- 调用某个 API
- 操作文件
- 调用 MCP Server
- 运行某个工具容器

在 AgentX 中，工具能力通过 Tool 模块、MCP Gateway、Docker 管理等模块协作完成。

### 6. RAG 知识库

RAG 用于让 Agent 基于用户上传的知识库回答问题。

典型流程是：

```text
上传文件
  -> 文档解析
  -> 文档切分
  -> 向量化
  -> 存入 pgvector
  -> 用户提问时检索相关片段
  -> 注入 prompt
  -> 调用大模型回答
```

RAG 是学习 Agent 项目时非常重要的一块。

### 7. 长期记忆

长期记忆和 RAG 有相似之处，但关注对象不同：

- RAG 面向知识库文档
- Memory 面向用户长期信息和偏好

例如：

- 用户偏好
- 用户身份信息
- 长期事实
- 历史对话中值得保留的信息

长期记忆让 Agent 不只是回答当前问题，还能逐步“认识用户”。

### 8. 计费系统

Agent 平台通常需要商业化能力。AgentX 中包含：

- 账户余额
- 产品
- 订单
- 支付
- token 使用记录
- 模型调用扣费

这部分可以帮助你理解真实 AI 平台如何从技术系统走向商业系统。

### 9. 执行追踪和监控

Agent 执行过程往往不透明，因此 trace 很重要。

一次完整执行可能包含：

- 用户输入
- prompt 组织
- RAG 检索
- 工具调用
- 模型调用
- token 消耗
- 错误信息

执行追踪可以帮助开发者调试 Agent 行为。

## 技术架构概览

AgentX 可以先按下面的结构理解：

```text
Next.js 前端
  +
Spring Boot 后端
  +
PostgreSQL / pgvector / RabbitMQ / 对象存储 / Docker
  +
外部 LLM / MCP / 支付服务
```

### 前端

目录：

```text
agentx-frontend-plus
```

作用：

- 页面展示
- 表单配置
- 聊天交互
- API 调用
- 后台管理

### 后端

目录：

```text
AgentX/src/main/java/org/xhy
```

后端采用比较清晰的分层：

```text
interfaces      API 入口层
application     应用服务层
domain          领域层
infrastructure  基础设施层
```

### 数据库和基础设施

主要包括：

- PostgreSQL：业务数据
- pgvector：向量检索
- RabbitMQ：异步消息
- 对象存储：文件存储
- Docker：容器和工具运行环境

### 外部服务

主要包括：

- OpenAI、Anthropic 等 LLM Provider
- MCP Gateway
- 支付网关
- GitHub 或 MCP 社区服务

## 学习本项目的主线

学习 AgentX 最重要的一条线是：

```text
前端发起请求
  -> 后端组织 Agent 上下文
  -> RAG / 工具 / 记忆增强
  -> 调用模型
  -> 流式返回
  -> 存储、追踪、计费
```

后续所有模块都可以挂到这条主线上。

例如：

- Agent 模块：决定当前对话使用什么配置
- LLM 模块：决定调用哪个模型
- Message 模块：决定上下文如何组织
- Tool 模块：决定 Agent 能调用什么工具
- RAG 模块：决定哪些知识片段进入 prompt
- Memory 模块：决定哪些长期记忆进入上下文
- Billing 模块：决定如何记录和扣费
- Trace 模块：决定如何调试执行过程

只要这条链路清楚，整个项目就不会散。

## 本课建议阅读文件

本课不要求深入阅读源码，只建议先看这些文档和目录：

```text
README.md
docs/agentx-learning-guide.md
docs/agentx-course-outline.md
docs/agent_design.md
docs/develop_document.md
```

建议快速浏览这些目录：

```text
agentx-frontend-plus
AgentX/src/main/java/org/xhy/interfaces
AgentX/src/main/java/org/xhy/application
AgentX/src/main/java/org/xhy/domain
AgentX/src/main/java/org/xhy/infrastructure
AgentX/src/main/resources/db/migration
```

这一课的目标不是看懂每一行，而是知道“未来要去哪里找答案”。

## 关键概念速记

### Agent

一个可配置的智能体，包含 prompt、模型、工具、知识库等配置。

### Session

一次连续对话的会话。

### Message

会话中的一条消息，可能来自用户，也可能来自模型。

### Context

模型调用时需要的上下文，包括历史消息、系统提示词、RAG 片段、记忆等。

### Provider

模型服务商，例如 OpenAI、Anthropic 或用户自定义服务商。

### Model

具体模型，例如某个 chat model 或 embedding model。

### Tool

Agent 可以调用的工具能力。

### MCP

一种让模型或 Agent 调用外部工具和服务的协议生态。

### RAG

检索增强生成。先检索相关知识，再让模型基于知识回答。

### Memory

长期记忆。用于保存用户偏好、事实和历史信息。

### SSE

Server-Sent Events。后端向前端持续推送模型生成内容的流式通信方式。

## 本课小结

AgentX 的学习难点不在某一个技术点，而在于它把很多能力组合在了一起：

- Agent 配置
- 对话上下文
- LLM 调用
- 工具调用
- RAG
- 记忆
- 计费
- trace
- 前端管理后台

因此学习时不要一开始追求“全部看懂”。更好的策略是：

1. 先理解产品能做什么。
2. 再理解整体架构。
3. 然后死磕一次对话主链路。
4. 最后把 RAG、工具、记忆、计费、监控逐个挂上去。

## 课后练习

### 练习 1：用一句话描述 AgentX

尝试用自己的话回答：

```text
AgentX 是什么？
```

建议答案格式：

```text
AgentX 是一个用于创建、配置、运行和运营智能 Agent 的开源平台。
```

### 练习 2：列出 AgentX 的 5 个核心模块

至少写出 5 个模块，例如：

- Agent 管理
- 对话系统
- 模型服务商
- 工具/MCP
- RAG 知识库
- 长期记忆
- 计费系统
- 执行追踪

### 练习 3：画出一次对话的大致流程

不用画得很细，只要表达下面这条线即可：

```text
前端
  -> 后端 API
  -> 会话和 Agent 配置
  -> 上下文组织
  -> RAG / 工具 / 记忆
  -> LLM
  -> SSE 返回
  -> 消息保存和计费
```

### 练习 4：浏览核心目录

打开项目，快速浏览下面目录，每个目录只需要记住它大概负责什么：

```text
AgentX/src/main/java/org/xhy/interfaces
AgentX/src/main/java/org/xhy/application
AgentX/src/main/java/org/xhy/domain
AgentX/src/main/java/org/xhy/infrastructure
agentx-frontend-plus
```

## 下一课预告

第 2 课会进入本地环境与项目启动。

下一课重点解决：

- 如何启动项目
- 各个服务端口分别是什么
- `.env` 应该如何配置
- Docker 部署和源码启动有什么区别
- 启动失败时应该从哪里排查

从第 2 课开始，你会真正把 AgentX 跑起来，然后用产品体验反推源码结构。
