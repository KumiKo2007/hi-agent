# 第 21 课：工具调用基础（小白版）

## 这节课要解决什么问题

前面我们已经学会了 Agent 如何聊天。

但真正的 Agent 不应该只会聊天，还应该会做事。

比如：

- 查询天气
- 搜索资料
- 调用外部接口
- 操作文件
- 查询数据库
- 使用 MCP 工具

这些“做事能力”在 AgentX 里叫：

```text
工具。
```

## 本课学习目标

学完本课后，你应该能回答：

1. 工具是什么？
2. 工具市场是什么？
3. 用户安装工具是什么意思？
4. Agent 的 `toolIds` 有什么用？
5. `UserToolEntity` 保存什么？
6. `ToolProvider` 是什么？
7. 内置工具和外部工具有什么区别？
8. 工具预设参数是什么？

## 先用“员工的工具箱”理解

一个员工要完成工作，可能需要工具。

比如客服员工需要：

- 客户资料系统
- 订单查询系统
- 退款系统

Agent 也是一样。

普通大模型只会生成文字。

但如果给它工具，它就能：

```text
根据需要调用工具，再把工具结果整理给用户。
```

所以你可以先记住：

```text
工具让 Agent 从“会说”变成“会做”。
```

## 工具市场是什么

工具市场入口在：

```text
E:\agentx\AgentX\AgentX\src\main\java\org\xhy\interfaces\api\portal\tool\PortalToolController.java
```

里面有这些接口：

| 接口 | 小白解释 |
| --- | --- |
| `POST /tools` | 上传工具 |
| `GET /tools/market` | 查看工具市场 |
| `POST /tools/install/{toolId}/{version}` | 安装工具 |
| `POST /tools/uninstall/{toolId}` | 卸载工具 |
| `GET /tools/installed` | 查看已安装工具 |
| `GET /tools/recommend` | 推荐工具 |

你可以把它理解成：

```text
工具市场像应用商店，用户可以安装工具。
```

## ToolEntity：工具档案

工具实体在：

```text
E:\agentx\AgentX\AgentX\src\main\java\org\xhy\domain\tool\model\ToolEntity.java
```

它对应工具的基础档案。

核心字段：

| 字段 | 小白解释 |
| --- | --- |
| `id` | 工具 ID |
| `name` | 工具名称 |
| `icon` | 图标 |
| `subtitle` | 副标题 |
| `description` | 描述 |
| `userId` | 上传者 |
| `labels` | 标签 |
| `toolType` | 工具类型，当前主要是 MCP |
| `uploadType` | 上传方式，比如 GitHub 或 zip |
| `uploadUrl` | 上传地址 |
| `installCommand` | 安装命令 |
| `toolList` | 工具能力列表 |
| `status` | 审核状态 |
| `mcpServerName` | MCP 服务名称 |
| `isGlobal` | 是否全局工具 |

一句话：

```text
ToolEntity 保存“这个工具是什么、怎么安装、里面有哪些能力”。
```

## UserToolEntity：用户安装的工具

用户安装工具后，会形成用户和工具的关系。

源码：

```text
E:\agentx\AgentX\AgentX\src\main\java\org\xhy\domain\tool\model\UserToolEntity.java
```

核心字段：

| 字段 | 小白解释 |
| --- | --- |
| `userId` | 哪个用户安装了 |
| `toolId` | 安装的是哪个工具 |
| `version` | 安装的工具版本 |
| `toolList` | 这个工具提供的能力 |
| `mcpServerName` | MCP 服务名 |
| `publicState` | 是否公开 |

这就像手机应用商店：

```text
应用商店里有 App，安装到你手机后，才成为“你的 App”。
```

## Agent 的 toolIds

第 11 课讲过，Agent 里有：

```text
toolIds
```

它表示：

```text
这个 Agent 被允许使用哪些工具。
```

注意两层关系：

| 层级 | 含义 |
| --- | --- |
| 用户安装工具 | 用户拥有这个工具 |
| Agent 配置 toolIds | 这个 Agent 可以用哪些工具 |

用户安装了很多工具，不代表每个 Agent 都能用全部工具。

Agent 只能使用自己配置里的工具。

## 聊天时怎么拿到工具

在第 17 课里，`ConversationAppService` 会做一步：

```text
getMcpServerNames(agent.getToolIds(), userId)
```

它的作用是：

```text
根据 Agent 的 toolIds，找到当前用户已安装工具对应的 MCP Server 名称。
```

然后把这些名称放进：

```text
ChatContext.mcpServerNames
```

后面真正处理聊天时，工具管理器会用它创建工具。

## AgentToolManager：工具管理器

源码：

```text
E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\message\agent\AgentToolManager.java
```

核心方法：

```java
public ToolProvider createToolProvider(...)
```

它会：

1. 遍历 MCP Server 名称。
2. 获取每个工具的 SSE URL。
3. 创建 MCP Client。
4. 加入预设参数。
5. 最后生成 LangChain4j 的 `ToolProvider`。

小白理解：

```text
AgentToolManager 把 Agent 配置里的工具，变成模型真正可以调用的工具箱。
```

## ToolProvider 是什么

`ToolProvider` 来自 LangChain4j。

你可以把它理解成：

```text
给大模型看的工具清单和调用入口。
```

模型回答时，如果判断需要工具，就会通过 `ToolProvider` 调用对应工具。

工具执行后，结果会回到模型，模型再整理成自然语言回答。

## 工具预设参数是什么

Agent 里有：

```text
toolPresetParams
```

它表示：

```text
某些工具调用时提前固定好的参数。
```

比如一个查询工具需要固定某个项目 ID、环境名、数据源名。

这些参数不希望每次都让模型猜。

所以可以提前放在 Agent 配置里。

小白理解：

```text
预设参数就像提前贴在工具上的使用说明和默认配置。
```

## 内置工具和外部工具

AgentX 里有两类工具概念：

| 类型 | 小白解释 |
| --- | --- |
| 内置工具 | 项目代码里自带的工具 |
| 外部工具 | 通过 MCP、市场、容器等方式接入的工具 |

内置工具注册器：

```text
E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\message\builtin\BuiltInToolRegistry.java
```

它会扫描带有 `@BuiltInTool` 注解的组件。

外部工具主要通过 MCP 接入。

第 22 课会专门讲 MCP。

## 工具调用时发生什么

在 `AbstractMessageHandler` 里，如果模型执行了工具，会触发：

```text
onToolExecuted
```

系统会：

1. 保存前面已经生成的 AI 文本。
2. 创建一条 `TOOL_CALL` 消息。
3. 给前端发送“执行工具：xxx”。
4. 继续等模型整理结果。

所以前端能看到工具调用过程。

## 本课小结

这一课你要记住：

```text
工具让 Agent 从“会回答”变成“会执行动作”。
```

核心点：

- 工具市场像应用商店
- `ToolEntity` 是工具档案
- `UserToolEntity` 是用户安装的工具
- Agent 的 `toolIds` 决定它能用哪些工具
- `AgentToolManager` 创建工具提供者
- `ToolProvider` 是模型调用工具的入口
- 工具预设参数用于提前固定工具调用参数
- 工具执行会产生 `TOOL_CALL` 消息

## 本课源码阅读路线

1. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\interfaces\api\portal\tool\PortalToolController.java`
2. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\domain\tool\model\ToolEntity.java`
3. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\domain\tool\model\UserToolEntity.java`
4. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\ConversationAppService.java`
5. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\message\agent\AgentMessageHandler.java`
6. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\message\agent\AgentToolManager.java`
7. `E:\agentx\AgentX\AgentX\src\main\java\org\xhy\application\conversation\service\message\builtin\BuiltInToolRegistry.java`

## 课后练习

### 练习 1：解释工具

请用一句话解释：

```text
工具对 Agent 有什么用？
```

参考答案：

```text
工具让 Agent 能调用外部能力完成具体动作，而不只是生成文字。
```

### 练习 2：区分 ToolEntity 和 UserToolEntity

参考答案：

```text
ToolEntity 是工具本身的档案，UserToolEntity 是某个用户安装了某个工具的记录。
```

## 下一课预告

下一课讲：

```text
MCP 集成机制。
```

也就是外部工具到底如何通过 MCP Gateway、MCP Server、容器被后端调用。
