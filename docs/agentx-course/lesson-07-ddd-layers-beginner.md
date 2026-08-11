# 第 7 课：DDD 分层思想（小白版）

## 这节课要解决什么问题

第 6 课我们认识了后端入口：

```text
Controller 接收前端请求。
```

但一个请求进入 Controller 以后，后端并不会把所有代码都写在 Controller 里。

AgentX 后端把代码分成了几层：

```text
interfaces
application
domain
infrastructure
```

这一课就来讲：

```text
为什么要分层？
每一层分别负责什么？
一次请求怎么从一层走到下一层？
```

## 本课学习目标

学完本课后，你应该能回答：

1. 为什么后端代码不能全部写在 Controller 里？
2. `interfaces` 层负责什么？
3. `application` 层负责什么？
4. `domain` 层负责什么？
5. `infrastructure` 层负责什么？
6. AppService 和 DomainService 有什么区别？
7. Repository 是什么？
8. 一次请求如何从前端走到数据库？

## 先用餐厅理解分层

假设你去餐厅点一份炒饭。

整个过程不是一个人完成的。

通常会这样：

```text
顾客点餐
  ↓
服务员记录需求
  ↓
大堂经理安排厨房
  ↓
厨师按照菜谱做饭
  ↓
仓库提供食材
  ↓
服务员把炒饭端回来
```

对应到后端分层：

| 餐厅角色 | 后端层 |
| --- | --- |
| 服务员 | interfaces |
| 大堂经理 | application |
| 菜谱和厨师规则 | domain |
| 仓库、冰箱、燃气 | infrastructure |

这就是分层思想。

每一层只做自己擅长的事情。

## 为什么要分层

如果一个人既接待顾客、又做饭、又管仓库、又收钱，餐厅会很混乱。

代码也是一样。

如果 Controller 里写了所有逻辑：

- 接收请求
- 校验参数
- 查数据库
- 判断权限
- 调用模型
- 保存消息
- 处理异常
- 返回结果

这个 Controller 很快会变得非常长，非常难维护。

所以项目会分层。

分层的好处：

```text
每层职责清楚。
代码更容易找。
修改某个功能时影响范围更小。
多人协作时不容易混乱。
```

## AgentX 后端的四层

AgentX 后端主要分成：

```text
interfaces
application
domain
infrastructure
```

用一句话记：

```text
interfaces 接请求。
application 排流程。
domain 定规则。
infrastructure 管技术。
```

下面逐个讲。

## 第一层：interfaces 接请求

路径：

```text
AgentX/src/main/java/org/xhy/interfaces
```

这一层是后端的入口。

前端发来的 HTTP 请求，通常先到这里。

它主要负责：

- 接收请求
- 读取参数
- 做基础参数校验
- 获取当前登录用户
- 调用 application 层
- 返回统一结果

比如：

```text
用户点击登录
  -> LoginController 接收请求
```

```text
管理员打开用户列表
  -> AdminUserController 接收请求
```

```text
用户创建 Agent
  -> PortalAgentController 接收请求
```

生活类比：

```text
interfaces 就是服务窗口。
它负责接待，但不负责完成所有业务。
```

## interfaces 层不应该做什么

它不应该写很多复杂业务。

比如下面这些不适合全写在 Controller 里：

- Agent 版本怎么发布
- 聊天上下文怎么组织
- RAG 怎么检索
- 账户余额怎么扣费
- 记忆怎么去重

Controller 应该尽量薄一点。

你可以理解为：

```text
Controller 只负责“接单”和“交单”。
```

## 第二层：application 排流程

路径：

```text
AgentX/src/main/java/org/xhy/application
```

这一层叫应用服务层。

它负责把一个完整业务流程串起来。

例如“一次聊天”可能要做：

1. 查询会话。
2. 查询 Agent。
3. 查询模型。
4. 准备历史消息。
5. 准备工具。
6. 准备 RAG。
7. 调用大模型。
8. 流式返回。
9. 保存消息。
10. 记录计费和 trace。

这些步骤的组织，通常在 application 层。

生活类比：

```text
application 像大堂经理。
它知道一件事要安排哪些部门配合完成。
```

## 什么是 AppService

你会在代码里看到很多：

```text
XxxAppService
```

比如：

```text
AgentAppService
ConversationAppService
ToolAppService
MemoryAppService
PaymentAppService
```

AppService 的作用是：

```text
组织一个完整用例的流程。
```

比如 `ConversationAppService` 就是对话流程的应用服务。

它不只是查一张表，而是把对话相关的很多模块串起来。

## 第三层：domain 定规则

路径：

```text
AgentX/src/main/java/org/xhy/domain
```

这一层叫领域层。

它负责核心业务规则。

例如：

- 用户是否存在
- Agent 是否属于当前用户
- Agent 状态是否允许发布
- 模型服务商是否可用
- 工具是否已安装
- 记忆如何去重
- RAG 检索如何排序

生活类比：

```text
domain 像菜谱、制度和规则。
```

它决定：

```text
什么事情可以做，什么事情不能做，具体规则是什么。
```

## 什么是 DomainService

你会看到很多：

```text
XxxDomainService
```

比如：

```text
AgentDomainService
UserDomainService
MessageDomainService
LLMDomainService
MemoryDomainService
```

DomainService 的作用是：

```text
处理某个业务领域里的核心规则。
```

比如 `AgentDomainService` 关心 Agent 自己的规则：

- Agent 怎么创建
- Agent 怎么更新
- Agent 怎么查询
- Agent 状态怎么变化

## AppService 和 DomainService 的区别

这是新手很容易混的地方。

简单记：

```text
AppService 管流程。
DomainService 管规则。
```

比如一次创建 Agent 的请求：

```text
Controller 接到请求
  ↓
AgentAppService 安排创建流程
  ↓
AgentDomainService 检查和保存 Agent 规则
```

再比如一次聊天请求：

```text
ConversationAppService 负责整个聊天流程
MessageDomainService 负责消息规则
AgentDomainService 负责 Agent 规则
LLMDomainService 负责模型配置规则
```

## 第四层：infrastructure 管技术

路径：

```text
AgentX/src/main/java/org/xhy/infrastructure
```

这一层负责底层技术细节。

比如：

- 数据库 Repository 实现
- MyBatis 配置
- 类型转换
- RabbitMQ 配置
- SSE 消息传输
- 文件存储
- LLM 协议适配
- 全局异常处理

生活类比：

```text
infrastructure 像餐厅里的仓库、冰箱、电力、燃气和配送系统。
```

业务人员不需要每天关心电线怎么接，但系统必须依赖它。

## 什么是 Repository

Repository 可以理解为：

```text
后端访问数据库的工具。
```

比如：

- UserRepository 访问用户表
- AgentRepository 访问 Agent 表
- MessageRepository 访问消息表
- OrderRepository 访问订单表

生活类比：

```text
Repository 像仓库管理员。
你要拿数据、存数据，都要通过它。
```

在 AgentX 里，很多 Repository 在：

```text
domain/*/repository
```

它们通常会配合 MyBatis Plus 访问数据库。

## 一次请求完整走法

以“后台查看用户列表”为例。

大致流程：

```text
浏览器打开后台用户页面
  ↓
前端请求用户列表 API
  ↓
AdminUserController 接请求
  ↓
UserAppService 安排查询流程
  ↓
UserDomainService 处理用户相关规则
  ↓
UserRepository 查询数据库
  ↓
数据库返回用户数据
  ↓
后端组装返回结果
  ↓
前端显示用户列表
```

这就是分层后的请求流动。

## 再看一次聊天请求

聊天更复杂，但层次也类似：

```text
前端发送聊天消息
  ↓
Conversation Controller 接请求
  ↓
ConversationAppService 编排聊天流程
  ↓
AgentDomainService 获取 Agent 规则和配置
  ↓
MessageDomainService 处理消息和上下文
  ↓
ToolDomainService 准备工具
  ↓
RagSearchService 检索知识库
  ↓
LLM 相关服务调用模型
  ↓
SSE 返回结果
  ↓
Repository 保存消息和记录
```

后面课程会重点拆这条主线。

## 为什么这叫 DDD

DDD 是 Domain-Driven Design 的缩写。

不用背英文。

可以先理解成：

```text
围绕业务领域来组织代码。
```

普通写法可能是按技术分类：

```text
controller
service
mapper
entity
```

DDD 更强调业务边界：

```text
agent
conversation
tool
rag
memory
payment
user
```

每个业务模块都有自己的模型、服务和规则。

这对大型项目更清晰。

## AgentX 里的业务领域

你可以在 `domain` 目录里看到很多领域：

```text
agent
conversation
tool
rag
memory
llm
user
order
payment
product
trace
scheduledtask
```

每个目录都代表系统中的一个业务领域。

比如：

- `agent` 负责智能体
- `conversation` 负责会话和消息
- `tool` 负责工具
- `rag` 负责知识库
- `memory` 负责长期记忆
- `llm` 负责模型和服务商
- `payment` 负责支付

## 新手怎么读分层代码

建议按照请求链路读，不要按目录从上到下读。

方法是：

```text
先找页面
  ↓
找前端 API
  ↓
找 Controller
  ↓
找 AppService
  ↓
找 DomainService
  ↓
找 Repository
```

这样你每一步都知道自己为什么看这个文件。

## 本课小结

这一课你要记住：

```text
interfaces 接请求。
application 排流程。
domain 定规则。
infrastructure 管技术。
```

再记一句：

```text
AppService 管一个完整业务流程。
DomainService 管某个业务领域里的核心规则。
Repository 管数据库访问。
```

这套分层不是为了显得高级，而是为了让复杂项目不乱。

## 课后练习

### 练习 1：说出四层职责

补全：

```text
interfaces：
application：
domain：
infrastructure：
```

### 练习 2：解释 AppService 和 DomainService

用自己的话解释：

```text
AppService 是什么？
DomainService 是什么？
```

建议答案：

```text
AppService 负责编排流程。
DomainService 负责业务规则。
```

### 练习 3：打开 domain 目录

找到：

```text
AgentX/src/main/java/org/xhy/domain
```

看看里面有哪些业务领域。

### 练习 4：找一个 AppService

找到：

```text
AgentX/src/main/java/org/xhy/application/conversation/service/ConversationAppService.java
```

先不用读懂，只看类名和大概结构。

### 练习 5：画出请求流

画出：

```text
Controller
  -> AppService
  -> DomainService
  -> Repository
  -> Database
```

## 下一课预告

第 8 课会讲用户、登录与鉴权。

也就是：

- 用户怎么登录
- token 是什么
- 为什么有些页面需要登录
- 为什么后台只有管理员能进
- 401 和 403 分别是什么意思
- 用户体系在 AgentX 里大概怎么组织
