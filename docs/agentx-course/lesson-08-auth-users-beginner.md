# 第 8 课：用户、登录与鉴权（小白版）

## 这节课要解决什么问题

前面我们已经知道：

- 前端页面负责展示和操作。
- 后端接口负责处理请求。
- 后端代码会分成多层。

这一课讲一个所有系统都绕不开的问题：

```text
系统怎么知道“你是谁”？
```

也就是：

- 用户是什么？
- 登录是什么？
- token 是什么？
- 为什么有些页面不登录不能看？
- 为什么后台只有管理员能进？
- 401 和 403 是什么意思？

这些内容叫：

```text
用户、登录与鉴权。
```

## 本课学习目标

学完本课后，你应该能回答：

1. 用户账号是什么？
2. 登录到底发生了什么？
3. token 是什么？
4. 后端怎么知道当前请求是谁发的？
5. 普通用户和管理员有什么区别？
6. 401 未授权是什么意思？
7. 403 无权限是什么意思？
8. AgentX 里用户相关代码大概在哪里？

## 先用游乐园理解登录

想象你去游乐园。

你买票之前，很多项目不能玩。

买票后，你会拿到门票或手环。

之后你去玩项目，工作人员看一下手环，就知道：

```text
你已经买票了。
```

系统登录也是类似的：

```text
输入账号密码
  ↓
后端检查账号密码是否正确
  ↓
正确就发给你一个 token
  ↓
以后每次请求都带上 token
  ↓
后端看到 token，就知道你是谁
```

所以 token 就像游乐园门票。

## 什么是用户

用户就是系统里的账号。

在 AgentX 里，用户可以：

- 登录系统
- 创建 Agent
- 使用 Agent 对话
- 配置模型服务商
- 安装工具
- 创建知识库
- 查看自己的账号信息

管理员用户还能：

- 管理所有用户
- 审核 Agent
- 管理工具
- 管理模型服务商
- 管理订单和商品
- 管理系统配置

## 普通用户和管理员

你可以这样理解：

```text
普通用户：使用系统。
管理员：管理系统。
```

普通用户能做：

- 创建自己的 Agent
- 使用自己的知识库
- 和 Agent 聊天
- 管理自己的配置

管理员能做：

- 查看用户列表
- 管理 Agent 市场
- 审核工具
- 管理模型服务商
- 查看订单
- 配置系统规则

后台管理地址：

```text
http://localhost:3000/admin
```

通常需要管理员账号。

默认管理员账号：

```text
admin@agentx.ai / admin123
```

默认测试账号：

```text
test@agentx.ai / test123
```

## 登录时发生了什么

你在登录页面输入：

```text
邮箱
密码
```

然后点击登录。

背后大概发生：

```text
前端把邮箱和密码发给后端
  ↓
后端查数据库，看看这个邮箱是否存在
  ↓
后端检查密码是否正确
  ↓
如果正确，生成 token
  ↓
后端把 token 和用户信息返回给前端
  ↓
前端保存 token
  ↓
之后访问其他接口时带上 token
```

如果邮箱或密码错误，后端会返回登录失败。

## 什么是 token

token 可以理解成：

```text
登录后的身份凭证。
```

你不用每次请求都重新输入账号密码。

只要带上 token，后端就能知道：

```text
这个请求来自哪个用户。
```

生活类比：

```text
账号密码 = 你买票时提供身份证明。
token = 买票后拿到的门票。
```

## token 放在哪里

通常前端会把 token 保存在浏览器里。

然后每次请求后端时，把 token 放到请求头里。

请求头可以理解成：

```text
请求附带的小纸条。
```

小纸条上写着：

```text
我是某某用户，这是我的登录凭证。
```

后端看到这个凭证，就能识别用户。

## 什么是鉴权

鉴权就是：

```text
判断你有没有权限做某件事。
```

它分两步：

### 第一步：认证

认证是判断：

```text
你是谁？
```

比如 token 能证明你是 `admin@agentx.ai`。

### 第二步：授权

授权是判断：

```text
你能不能做这件事？
```

比如：

```text
admin@agentx.ai 是管理员，可以进后台。
test@agentx.ai 是普通用户，不一定能进后台。
```

所以：

```text
认证解决“你是谁”。
授权解决“你能做什么”。
```

## 401 是什么意思

你可能会看到：

```text
401 Unauthorized
```

意思是：

```text
你还没登录，或者登录凭证无效。
```

常见情况：

- 没有 token
- token 过期
- token 格式错误
- token 被清除了

生活类比：

```text
你没有门票，不能进游乐园。
```

解决办法：

```text
重新登录。
```

## 403 是什么意思

你也可能看到：

```text
403 Forbidden
```

意思是：

```text
系统知道你是谁，但你没有权限做这件事。
```

比如普通用户访问后台管理。

生活类比：

```text
你有游乐园门票，但你不能进入员工办公室。
```

解决办法：

```text
换有权限的账号，或者让管理员给你权限。
```

## 401 和 403 的区别

简单记：

```text
401：你还没证明你是谁。
403：你已经证明了你是谁，但你没权限。
```

表格记：

| 状态码 | 含义 | 类比 |
| --- | --- | --- |
| 401 | 没登录或 token 无效 | 没门票 |
| 403 | 登录了但没权限 | 有门票，但不能进员工区 |

## AgentX 里的登录入口

前端登录页面会调用后端登录接口。

后端登录相关代码大概在：

```text
AgentX/src/main/java/org/xhy/interfaces/api/portal/user/LoginController.java
AgentX/src/main/java/org/xhy/application/user/service/LoginAppService.java
AgentX/src/main/java/org/xhy/domain/user/service/UserDomainService.java
```

不用急着看懂。

先知道这几个位置：

- Controller 接收登录请求
- AppService 安排登录流程
- DomainService 处理用户规则

## 用户相关代码在哪里

用户相关模块大概在：

```text
domain/user
application/user
interfaces/api/portal/user
interfaces/api/admin/user
```

分别可以理解为：

```text
domain/user：用户核心规则
application/user：用户相关流程
portal/user：普通用户接口
admin/user：管理员用户接口
```

## portal 和 admin 有什么区别

你会在接口目录里看到：

```text
portal
admin
```

可以这样理解：

```text
portal：普通用户入口。
admin：管理员入口。
```

比如：

```text
portal/user/LoginController
```

是普通登录相关接口。

```text
admin/user/AdminUserController
```

是后台用户管理相关接口。

## 当前用户是怎么传到后端的

用户登录后，前端保存 token。

之后每次请求，前端会带上 token。

后端收到请求后：

```text
读取 token
  ↓
解析 token
  ↓
得到用户 ID
  ↓
根据用户 ID 查询用户
  ↓
知道当前请求是谁发的
```

所以后端很多接口都需要知道：

```text
当前用户是谁？
```

比如：

- 创建 Agent 时，要知道是谁创建的。
- 查询工作区时，要知道查哪个用户的工作区。
- 发送聊天时，要知道是谁在聊天。
- 扣费时，要知道扣谁的账户。

## 为什么不能相信前端传来的 userId

新手可能会问：

```text
前端直接告诉后端 userId 不就行了吗？
```

不安全。

因为前端传来的东西可以被伪造。

比如有人把请求里的 userId 改成别人的 ID，就可能冒充别人。

所以后端不能只相信前端传来的 userId。

更安全的做法是：

```text
后端从 token 中解析当前用户。
```

这就是鉴权的重要性。

## 后台为什么需要管理员权限

后台管理能做很多敏感操作：

- 查看用户列表
- 管理所有 Agent
- 审核工具
- 管理订单
- 管理系统配置

这些不能让普通用户随便操作。

所以后台接口需要额外判断：

```text
当前用户是不是管理员？
```

如果不是，就应该拒绝。

这就是授权。

## 登录和数据库的关系

用户信息最终要保存在数据库里。

比如：

- 用户 ID
- 邮箱
- 密码加密结果
- 昵称
- 是否管理员
- 创建时间
- 状态

登录时，后端会根据邮箱查数据库。

如果用户不存在，登录失败。

如果密码不对，登录失败。

如果都正确，登录成功。

## 密码为什么不能明文保存

明文保存就是直接保存：

```text
admin123
```

这是非常危险的。

正常系统应该保存加密或哈希后的结果。

这样即使数据库泄露，也不能直接看到原始密码。

你现在不用掌握加密算法，只要记住：

```text
真实项目不能明文保存用户密码。
```

## 登录状态为什么会过期

token 通常不是永久有效的。

如果永久有效，别人拿到你的 token，就能一直冒充你。

所以系统会设置过期时间。

过期后，你需要重新登录。

这就是有时候页面突然要求重新登录的原因。

## 退出登录时发生什么

退出登录通常会做：

```text
前端清除 token
  ↓
跳转到登录页
```

之后再访问需要登录的接口，就会变成 401。

因为你没有带有效 token。

## 用浏览器观察登录请求

你可以打开浏览器开发者工具：

```text
F12 -> Network
```

然后登录系统。

你会看到前端向后端发送请求。

你现在不用看懂所有细节，只要观察：

- 请求地址是什么
- 请求方法是 POST 还是 GET
- 返回状态码是不是成功
- 返回里有没有 token 或用户信息

这对后面学习前后端连接很有帮助。

## 本课对应源码目录

重点目录：

```text
AgentX/src/main/java/org/xhy/interfaces/api/portal/user
AgentX/src/main/java/org/xhy/interfaces/api/admin/user
AgentX/src/main/java/org/xhy/application/user
AgentX/src/main/java/org/xhy/domain/user
AgentX/src/main/java/org/xhy/application/auth
AgentX/src/main/java/org/xhy/domain/auth
```

前端相关：

```text
agentx-frontend-plus/lib/account-service.ts
agentx-frontend-plus/lib/http-client.ts
agentx-frontend-plus/contexts/account-context.tsx
```

你现在不用全部看懂。

先知道：

```text
登录和用户相关代码，大多围绕 user、auth、account 这些名字。
```

## 本课小结

这一课你要记住：

```text
用户是系统里的账号。
登录是证明你是谁。
token 是登录后的凭证。
认证判断你是谁。
授权判断你能做什么。
401 表示没登录或凭证无效。
403 表示登录了但没权限。
管理员可以进入后台。
普通用户主要使用系统。
```

最重要的一句话：

```text
后端不能随便相信前端传来的身份信息，必须通过 token 和权限规则确认当前用户。
```

## 课后练习

### 练习 1：用管理员账号登录

打开：

```text
http://localhost:3000
```

使用：

```text
admin@agentx.ai / admin123
```

登录。

### 练习 2：打开后台

访问：

```text
http://localhost:3000/admin
```

确认管理员可以进入后台。

### 练习 3：解释 token

用自己的话回答：

```text
token 是什么？
```

建议答案：

```text
token 是登录成功后拿到的身份凭证，后续请求会带上它。
```

### 练习 4：区分 401 和 403

补全：

```text
401 表示：
403 表示：
```

建议答案：

```text
401 表示没登录或登录凭证无效。
403 表示登录了但没有权限。
```

### 练习 5：找到登录 Controller

尝试找到：

```text
AgentX/src/main/java/org/xhy/interfaces/api/portal/user/LoginController.java
```

先不用看懂代码，只观察文件名和目录位置。

## 下一课预告

第 9 课会讲数据库模型与 MyBatis Plus。

也就是：

- 数据库是什么
- 表是什么
- Entity 是什么
- Repository 是什么
- 用户、Agent、消息这些数据怎么保存
- 为什么代码里会有很多 `xxxRepository`

从第 9 课开始，我们会理解系统的数据是怎么落到数据库里的。
