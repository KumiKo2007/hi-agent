# 第 9 课：数据库模型与 MyBatis Plus（小白版）

## 这节课要解决什么问题

前面我们讲了用户、登录和权限。

这一课开始理解一个更基础的问题：

```text
系统里的数据到底存在哪里？
```

比如：

- 用户账号存在哪里？
- Agent 信息存在哪里？
- 聊天记录存在哪里？
- 知识库文件信息存在哪里？
- 订单和计费记录存在哪里？

答案是：

```text
数据库。
```

AgentX 主要使用 PostgreSQL 数据库，并且通过 MyBatis Plus 在 Java 代码里操作数据库。

## 本课学习目标

学完本课后，你应该能回答：

1. 数据库是什么？
2. 表是什么？
3. 一行数据是什么？
4. Entity 是什么？
5. Repository 是什么？
6. MyBatis Plus 是什么？
7. 数据库迁移脚本在哪里？
8. 怎么把 Java 代码和数据库表对应起来？

## 先用 Excel 理解数据库

如果你没有数据库基础，可以先把数据库理解成：

```text
很多张 Excel 表组成的系统。
```

比如一张用户表：

| id | email | nickname | is_admin |
| --- | --- | --- | --- |
| 1 | admin@agentx.ai | 管理员 | true |
| 2 | test@agentx.ai | 测试用户 | false |

一张 Agent 表：

| id | name | user_id | description |
| --- | --- | --- | --- |
| a1 | 英语老师 | 2 | 帮助学习英语 |
| a2 | 编程助手 | 2 | 帮助学习代码 |

数据库就是很多这样的表。

## 数据库为什么重要

如果没有数据库，系统一重启，所有东西都会丢。

数据库负责长期保存：

- 用户
- Agent
- 会话
- 消息
- 工具
- 知识库
- 文件信息
- 订单
- 支付记录
- 执行 trace

你可以把数据库理解成：

```text
系统的长期记忆本。
```

## PostgreSQL 是什么

PostgreSQL 是一种数据库软件。

AgentX 使用它保存业务数据。

你现在不用掌握复杂 SQL，只要知道：

```text
PostgreSQL 是 AgentX 存数据的地方。
```

项目启动后，PostgreSQL 在容器里运行。

端口通常是：

```text
5432
```

## pgvector 是什么

AgentX 还使用了 pgvector。

pgvector 是 PostgreSQL 的扩展。

它用于向量检索。

如果你暂时不懂向量，可以先这样理解：

```text
pgvector 帮系统从知识库里找到和问题最相关的内容。
```

比如你上传了一份文档，用户问了一个问题。

系统会先从文档里找相关片段，再让大模型回答。

这个能力属于后面 RAG 课程的重点。

## 什么是表

表就是同一类数据的集合。

比如：

```text
users 表：保存用户。
agents 表：保存 Agent。
messages 表：保存聊天消息。
orders 表：保存订单。
```

生活类比：

```text
表就像 Excel 里的一个工作表。
```

## 什么是一行数据

表里的一行，就是一条记录。

比如用户表里的一行：

```text
admin@agentx.ai / 管理员 / 是否管理员
```

就是一个用户记录。

Agent 表里的一行，就是一个 Agent 记录。

消息表里的一行，就是一条聊天消息。

## 什么是字段

字段就是表里的列。

比如用户表可能有：

```text
id
email
password
nickname
is_admin
created_at
updated_at
```

每个字段保存一个具体信息。

## 什么是主键 id

每条数据通常都有一个唯一 ID。

这个 ID 就像身份证号。

比如：

```text
用户 A 有自己的 id。
用户 B 有自己的 id。
```

即使两个用户昵称一样，系统也能通过 ID 区分他们。

## 什么是外键或关联 ID

假设一个 Agent 是某个用户创建的。

Agent 表里可能会有：

```text
user_id
```

它表示：

```text
这个 Agent 属于哪个用户。
```

这就是表之间的关联。

生活类比：

```text
用户表是人员档案。
Agent 表里写着“这个 Agent 的主人是谁”。
```

## 什么是 Entity

Entity 可以理解成：

```text
数据库表在 Java 代码里的样子。
```

比如数据库里有用户表。

Java 代码里就可能有：

```text
UserEntity
```

数据库里有 Agent 表。

Java 代码里就可能有：

```text
AgentEntity
```

数据库里有消息表。

Java 代码里就可能有：

```text
MessageEntity
```

Entity 的作用是：

```text
让 Java 程序能用对象的方式操作数据库数据。
```

## Entity 长什么样

一个 Entity 大概会有很多字段。

比如用户 Entity 可能类似：

```java
public class UserEntity {
    private String id;
    private String email;
    private String nickname;
    private Boolean isAdmin;
}
```

你不用现在看懂 Java 细节。

只要理解：

```text
Entity 里的字段，通常对应数据库表里的字段。
```

## 什么是 Repository

Repository 可以理解成：

```text
专门帮代码访问数据库的工具。
```

比如：

```text
UserRepository：操作用户数据。
AgentRepository：操作 Agent 数据。
MessageRepository：操作消息数据。
OrderRepository：操作订单数据。
```

生活类比：

```text
Repository 像仓库管理员。
你要存东西、取东西，都找它。
```

## 为什么不直接在业务代码里写数据库操作

如果每个业务类都直接写数据库操作，代码会乱。

所以项目通常会把数据库访问集中到 Repository。

好处是：

```text
业务代码更清楚。
数据库操作更集中。
以后修改也更方便。
```

## 什么是 MyBatis Plus

MyBatis Plus 是 Java 里操作数据库的工具。

你可以先理解成：

```text
它帮 Java 代码更方便地查数据库、插入数据、更新数据、删除数据。
```

没有它，你可能要写很多 SQL。

有了它，很多简单操作可以直接调用方法。

比如：

```java
selectById(id)
insert(entity)
updateById(entity)
deleteById(id)
```

先不用背。

只要知道：

```text
MyBatis Plus 是 Java 和数据库之间的帮手。
```

## CRUD 是什么

你可能会看到 CRUD。

它表示四种基础数据操作：

| 缩写 | 含义 | 例子 |
| --- | --- | --- |
| C | Create 新增 | 创建用户 |
| R | Read 查询 | 查询用户列表 |
| U | Update 更新 | 修改昵称 |
| D | Delete 删除 | 删除记录 |

大部分管理后台页面，本质上都离不开 CRUD。

比如后台用户管理：

- 查询用户列表：Read
- 修改用户状态：Update
- 删除用户：Delete

## 数据库脚本在哪里

AgentX 的数据库脚本主要在：

```text
AgentX/src/main/resources/db
```

其中迁移脚本在：

```text
AgentX/src/main/resources/db/migration
```

你可以把 migration 理解为：

```text
数据库表结构的历史记录。
```

项目不断开发，数据库表也会变化。

比如：

- 新增用户字段
- 新增订单表
- 新增 trace 表
- 新增 RAG 发布表
- 新增消息 token 字段

这些变化就放在 migration 脚本里。

## migration 文件名怎么看

你会看到类似：

```text
V20250726001__create_billing_system_tables.sql
V20250731002__create_agent_execution_trace_tables.sql
V20250803001_add_messages_body_token_count.sql
```

不用害怕。

它大概表示：

```text
什么时候新增了什么数据库结构。
```

比如：

```text
create_billing_system_tables
```

意思是创建计费系统相关表。

## 怎么把 Entity 和表对应起来

看代码时，可以这样找：

```text
先找 Entity
  ↓
看 Entity 名字
  ↓
猜数据库表名
  ↓
去 migration 或 SQL 脚本里搜索表名
```

比如：

```text
AgentEntity
```

很可能对应 Agent 相关表。

```text
MessageEntity
```

很可能对应消息表。

```text
MemoryItemEntity
```

很可能对应记忆条目表。

## 用户数据的大致链路

以登录为例：

```text
用户输入邮箱和密码
  ↓
前端发登录请求
  ↓
LoginController 接请求
  ↓
LoginAppService 安排登录流程
  ↓
UserDomainService 检查用户规则
  ↓
UserRepository 查询数据库
  ↓
数据库返回用户记录
  ↓
后端判断密码是否正确
  ↓
返回登录结果
```

这说明：

```text
用户最终是存在数据库里的。
```

## Agent 数据的大致链路

创建 Agent 时：

```text
前端提交 Agent 名称、描述、提示词
  ↓
Controller 接请求
  ↓
AgentAppService 编排创建流程
  ↓
AgentDomainService 处理 Agent 规则
  ↓
AgentRepository 保存到数据库
```

所以你在页面上创建的 Agent，最终也会变成数据库里的一条记录。

## 聊天消息的大致链路

发送消息时：

```text
用户发送问题
  ↓
后端调用模型生成回答
  ↓
MessageDomainService 处理消息
  ↓
MessageRepository 保存消息
  ↓
数据库里多出聊天记录
```

所以聊天记录也不是凭空存在，它们会保存到数据库。

## 什么是软删除

有些系统删除数据时，并不是真的从数据库里删掉。

而是打一个标记：

```text
deleted_at
is_deleted
status
```

这叫软删除。

生活类比：

```text
不是把文件撕掉，而是把文件放进“已作废”文件夹。
```

好处是：

- 可以恢复
- 可以审计
- 不容易误删重要数据

## 什么是分页查询

如果用户表有 10 万条数据，不可能一次全部返回给前端。

所以要分页。

比如：

```text
第 1 页，每页 20 条
第 2 页，每页 20 条
第 3 页，每页 20 条
```

后台用户列表、订单列表、Agent 列表通常都会用分页。

## 本课对应源码目录

后端 Entity：

```text
AgentX/src/main/java/org/xhy/domain
```

各业务模块下面通常有：

```text
model
repository
service
```

数据库迁移：

```text
AgentX/src/main/resources/db/migration
```

基础 Repository：

```text
AgentX/src/main/java/org/xhy/infrastructure/repository
```

## 新手怎么看数据库代码

建议按这个顺序：

```text
先看页面是什么数据
  ↓
找对应 Entity
  ↓
找对应 Repository
  ↓
找 DomainService 怎么用 Repository
  ↓
最后再看 SQL 或 migration
```

不要一上来就硬读所有 SQL。

## 本课小结

这一课你要记住：

```text
数据库是系统保存数据的地方。
表像 Excel 工作表。
一行是一条记录。
字段是表里的列。
Entity 是数据库表在 Java 代码里的样子。
Repository 是访问数据库的工具。
MyBatis Plus 是帮助 Java 操作数据库的框架。
Migration 是数据库结构变化记录。
```

最重要的一句话：

```text
页面上看到的数据，最终大多来自数据库；页面上新增的数据，最终也会保存到数据库。
```

## 课后练习

### 练习 1：解释数据库

用自己的话回答：

```text
数据库是什么？
```

建议答案：

```text
数据库是系统长期保存数据的地方。
```

### 练习 2：解释 Entity

用自己的话回答：

```text
Entity 是什么？
```

建议答案：

```text
Entity 是数据库表在 Java 代码里的表示。
```

### 练习 3：找到 migration 目录

找到：

```text
AgentX/src/main/resources/db/migration
```

看看里面有哪些 SQL 文件。

### 练习 4：找一个 Repository

在项目里搜索：

```text
UserRepository
```

或者：

```text
AgentRepository
```

观察它们位于哪个目录。

### 练习 5：画出查询用户流程

画出：

```text
前端用户列表页面
  -> AdminUserController
  -> UserAppService
  -> UserDomainService
  -> UserRepository
  -> 数据库
```

## 下一课预告

第 10 课会讲配置系统与环境变量。

也就是：

- `.env` 是什么
- `application.yml` 是什么
- 为什么端口、数据库密码、模型 Key 不直接写死在代码里
- Docker 启动时配置怎么生效
- 新手应该重点看哪些配置
