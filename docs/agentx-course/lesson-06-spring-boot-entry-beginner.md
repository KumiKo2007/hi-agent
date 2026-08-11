# 第 6 课：Spring Boot 后端入口（小白版）

## 这节课要解决什么问题

第 5 课我们认识了项目目录。

从这一课开始，我们正式进入后端代码。

不过别紧张。我们不会一上来就看复杂业务，而是先搞懂最基础的问题：

```text
前端点了一个按钮，请求到底是怎么进入后端的？
```

后端入口的核心概念是：

```text
Controller
```

你可以把 Controller 理解成：

```text
后端接待前端请求的窗口。
```

## 本课学习目标

学完本课后，你应该能回答：

1. 什么是后端？
2. 什么是 API？
3. 什么是 Controller？
4. 前端为什么要调用后端 API？
5. `@RestController` 是什么？
6. Request 和 Response 分别是什么？
7. 为什么后端要做参数校验？
8. 为什么需要统一返回和异常处理？

## 先用生活例子理解

假设你去银行办业务。

银行有很多窗口：

- 存钱窗口
- 取钱窗口
- 开卡窗口
- 查询窗口
- 投诉窗口

你不会直接冲进银行后台找数据库。

你会先去对应窗口，把需求告诉工作人员。

对应到后端：

| 银行里的东西 | 后端里的东西 |
| --- | --- |
| 办业务窗口 | Controller |
| 办业务请求 | API 请求 |
| 你填写的表单 | Request |
| 工作人员给你的结果 | Response |
| 银行后台系统 | Service / Domain / Database |

所以 Controller 就是后端对外的办事窗口。

## 什么是后端

前端是你看到的页面。

后端是页面背后真正处理事情的服务。

比如你在页面上点击“登录”：

```text
前端负责显示登录框。
后端负责判断账号密码是否正确。
```

你打开“后台用户列表”：

```text
前端负责显示表格。
后端负责从数据库查出用户数据。
```

你发送聊天消息：

```text
前端负责显示聊天框。
后端负责组织上下文、调用大模型、保存消息。
```

## 什么是 API

API 可以先理解为：

```text
前端和后端沟通的固定接口。
```

比如：

```text
登录接口
用户列表接口
创建 Agent 接口
发送聊天消息接口
上传文件接口
```

每个接口都有一个地址。

例如：

```text
http://localhost:8088/api/health
```

这个就是健康检查 API。

它的作用是告诉你：

```text
后端服务现在是否正常。
```

## 什么是 HTTP 请求

前端和后端通常通过 HTTP 通信。

你现在不用深入协议细节，只要知道：

```text
浏览器访问网页也是 HTTP。
前端调用后端接口也是 HTTP。
```

常见请求方式有：

| 方法 | 大概意思 |
| --- | --- |
| GET | 获取数据 |
| POST | 提交新数据 |
| PUT | 更新数据 |
| DELETE | 删除数据 |

举例：

```text
GET /admin/users       获取用户列表
POST /login            提交登录信息
PUT /agents/{id}       更新 Agent
DELETE /tools/{id}     删除工具
```

先记住：

```text
GET 多用于查。
POST 多用于提交。
PUT 多用于改。
DELETE 多用于删。
```

## 什么是 Controller

Controller 是后端接收请求的类。

在 Spring Boot 里，Controller 通常会写：

```java
@RestController
public class XxxController {
}
```

`@RestController` 的意思可以先理解为：

```text
这个类是一个后端接口入口。
```

比如用户相关接口可能在：

```text
PortalUserController
AdminUserController
```

Agent 相关接口可能在：

```text
PortalAgentController
AdminAgentController
```

## AgentX 的 Controller 在哪里

后端入口层主要在：

```text
AgentX/src/main/java/org/xhy/interfaces
```

里面有很多 Controller。

例如：

```text
interfaces/api/HealthController.java
interfaces/api/portal/user/LoginController.java
interfaces/api/portal/agent/PortalAgentController.java
interfaces/api/admin/user/AdminUserController.java
interfaces/api/admin/AdminAgentController.java
interfaces/api/portal/rag/RagSearchController.java
```

你现在不用打开所有文件。

只要知道：

```text
想找后端接口入口，就先去 interfaces 目录。
```

## 一个请求进入后端的大致流程

比如你打开后台用户管理页面：

```text
http://localhost:3000/admin/users
```

页面需要用户列表。

流程大概是：

```text
前端页面加载
  ↓
前端调用后端用户列表 API
  ↓
请求进入 AdminUserController
  ↓
Controller 检查参数和权限
  ↓
Controller 调用 Application Service
  ↓
Service 查询用户数据
  ↓
后端返回用户列表
  ↓
前端显示表格
```

Controller 只负责“接请求”和“转交任务”。

它不应该做太多复杂业务。

## Controller 不应该什么都做

新手很容易以为：

```text
请求到了 Controller，那所有事情都写在 Controller 里就好了。
```

但大型项目通常不会这样。

因为 Controller 如果什么都做，会变得又长又乱。

正确思路更像：

```text
Controller：接待窗口
Application Service：安排流程
Domain Service：处理规则
Repository：访问数据库
```

生活类比：

```text
银行窗口不会亲自改数据库。
窗口只负责收材料、检查基本信息、把业务交给后台系统处理。
```

## 什么是 Request

Request 就是前端发给后端的数据。

比如登录时，前端要发：

```text
邮箱
密码
```

这就是登录请求数据。

在 Java 里，通常会有一个 Request 类来接收这些数据。

比如：

```text
LoginRequest
CreateAgentRequest
UpdateAgentRequest
QueryUserRequest
```

这些类一般放在：

```text
interfaces/dto
```

Request 可以理解为：

```text
前端交给后端的表单。
```

## 什么是 Response

Response 就是后端返回给前端的数据。

比如登录成功后，后端可能返回：

```text
用户信息
token
是否管理员
```

比如查询用户列表，后端可能返回：

```text
用户列表
总数量
当前页码
```

Response 可以理解为：

```text
后端办完业务后给前端的结果。
```

## 什么是 DTO

你会在代码里看到 DTO。

DTO 是 Data Transfer Object 的缩写。

不用背英文。

先理解成：

```text
专门用来传数据的对象。
```

比如：

- Request DTO：前端传给后端的数据
- Response DTO：后端返回给前端的数据
- Application DTO：后端内部层之间传递的数据

生活类比：

```text
DTO 就像一张表格或快递单。
它只负责装数据，不负责处理业务。
```

## 为什么要参数校验

用户提交的数据不一定可靠。

比如：

- 邮箱为空
- 密码太短
- Agent 名称为空
- 页码是负数
- 文件格式不支持

后端不能完全相信前端。

所以后端要做参数校验。

比如：

```text
邮箱不能为空。
密码不能为空。
名称长度不能超过限制。
页码必须大于 0。
```

这就像你去银行填表，工作人员会先检查：

```text
姓名有没有填？
身份证号格式对不对？
签名有没有漏？
```

## 什么是统一返回

如果每个接口返回格式都不一样，前端会很难处理。

所以很多项目会设计统一返回格式。

比如：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

你可以理解为：

```text
所有后端接口都用差不多的包装盒返回结果。
```

成功时：

```text
code 表示成功
data 放真正的数据
```

失败时：

```text
code 表示错误
message 说明原因
```

AgentX 里统一返回相关代码可以关注：

```text
interfaces/api/common/Result.java
```

## 什么是异常处理

异常就是程序运行过程中出了问题。

比如：

- 用户不存在
- 密码错误
- 没有权限
- 数据库连接失败
- 模型调用失败
- 文件上传失败

如果不统一处理异常，前端可能收到一堆难懂的错误。

所以后端一般会有全局异常处理。

在 AgentX 中可以关注：

```text
infrastructure/config/GlobalExceptionHandler.java
```

它的作用类似：

```text
把后端内部错误转换成前端能理解的错误信息。
```

## 什么是登录 token

用户登录成功后，后端通常会返回一个 token。

token 可以理解成：

```text
登录凭证。
```

你去游乐园买票后，会拿到门票。

后面玩项目时，工作人员看你的票，就知道你已经买过票。

在系统里也是这样：

```text
登录成功后拿到 token。
后续请求带上 token。
后端看到 token，就知道你是谁。
```

如果没有 token，访问某些接口就会返回：

```text
401 未授权
```

这就是为什么你访问：

```text
http://localhost:8088/api
```

可能看到 401。

说明：

```text
这个接口需要登录。
```

## 什么是 404

你访问：

```text
http://localhost:8088
```

可能看到 404。

404 的意思是：

```text
这个地址没有对应资源。
```

这不一定说明后端坏了。

因为后端根路径可能没有页面。

正确测试后端健康状态应该访问：

```text
http://localhost:8088/api/health
```

只要这里正常，说明后端在工作。

## 一个最简单的健康检查接口

健康检查接口可以理解为：

```text
问后端：你还活着吗？
```

地址是：

```text
http://localhost:8088/api/health
```

对应代码可以看：

```text
AgentX/src/main/java/org/xhy/interfaces/api/HealthController.java
```

这个文件很适合新手作为第一个 Controller 阅读。

因为它通常比较简单。

## 新手阅读 Controller 的方法

打开一个 Controller 时，不要从第一行开始硬读。

按这个顺序看：

### 第一步：看类名

比如：

```text
AdminUserController
```

大概知道：

```text
这是后台用户管理接口。
```

### 第二步：看路径

找类似：

```java
@RequestMapping(...)
@GetMapping(...)
@PostMapping(...)
```

它们告诉你接口地址。

### 第三步：看方法名

比如：

```text
getUsers
createAgent
login
deleteTool
```

方法名通常能说明这个接口做什么。

### 第四步：看参数

看它接收什么 Request。

### 第五步：看它调用哪个 Service

这一步最重要。

Controller 通常会调用 AppService。

找到 AppService 后，你就能继续追踪业务流程。

## 以后台用户管理为例

后台用户管理页面是：

```text
http://localhost:3000/admin/users
```

前端页面大概在：

```text
agentx-frontend-plus/app/(main)/admin/users/page.tsx
```

前端服务可能在：

```text
agentx-frontend-plus/lib/admin-user-service.ts
```

后端入口可能在：

```text
interfaces/api/admin/user/AdminUserController.java
```

业务服务可能在：

```text
application/user
domain/user
```

这就是完整追踪路线：

```text
页面
  -> 前端 service
  -> 后端 Controller
  -> AppService
  -> DomainService
  -> Repository / Database
```

## 本课对应源码目录

重点目录：

```text
AgentX/src/main/java/org/xhy/interfaces
```

重点文件：

```text
interfaces/api/HealthController.java
interfaces/api/common/Result.java
infrastructure/config/GlobalExceptionHandler.java
```

前端对应可以看：

```text
agentx-frontend-plus/lib/http-client.ts
agentx-frontend-plus/lib/api-config.ts
agentx-frontend-plus/lib/admin-user-service.ts
```

新手阶段不要求全部看懂，先知道它们各自负责什么。

## 本课小结

这一课你要记住：

```text
API 是前端和后端沟通的接口。
Controller 是后端接收请求的入口。
Request 是前端传来的数据。
Response 是后端返回的数据。
DTO 是用来传数据的对象。
参数校验是为了防止错误输入。
统一返回是为了让前端好处理结果。
异常处理是为了把错误变得可理解。
```

最重要的一句话：

```text
Controller 不负责做所有事，它主要负责接请求，然后把任务交给后面的 Service。
```

## 课后练习

### 练习 1：打开健康检查接口

浏览器打开：

```text
http://localhost:8088/api/health
```

确认后端正常。

### 练习 2：找到 HealthController

在项目里找到：

```text
AgentX/src/main/java/org/xhy/interfaces/api/HealthController.java
```

观察它是不是比其他业务 Controller 简单。

### 练习 3：解释 Controller

用自己的话回答：

```text
Controller 是什么？
```

建议答案：

```text
Controller 是后端接收前端请求的入口。
```

### 练习 4：区分 Request 和 Response

用自己的话解释：

```text
Request 是什么？
Response 是什么？
```

建议答案：

```text
Request 是前端发给后端的数据。
Response 是后端返回给前端的数据。
```

### 练习 5：找一个后台接口

打开后台用户管理页面：

```text
http://localhost:3000/admin/users
```

然后尝试找到：

```text
agentx-frontend-plus/lib/admin-user-service.ts
```

看看里面有没有和 `/admin/users` 相关的接口地址。

## 下一课预告

第 7 课会讲 DDD 分层思想。

也就是继续解释：

- 为什么后端要分层
- Controller 为什么不能什么都做
- AppService 是什么
- DomainService 是什么
- Repository 是什么
- 一次请求如何从 Controller 一层层走到数据库

从第 7 课开始，我们会真正理解 AgentX 后端为什么这样组织代码。
