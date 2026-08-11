# 第 10 课：配置系统与环境变量（小白版）

## 这节课要解决什么问题

前面我们知道了：

- 数据保存在数据库里。
- 后端通过 Repository 访问数据库。
- AgentX 运行时依赖前端、后端、数据库、消息队列和外部模型服务。

这一课讲：

```text
这些服务的地址、端口、账号、密码、密钥是从哪里来的？
```

答案是：

```text
配置文件和环境变量。
```

## 本课学习目标

学完本课后，你应该能回答：

1. 配置是什么？
2. 为什么不能把所有配置写死在代码里？
3. `.env` 文件是什么？
4. `.env.example` 是什么？
5. `application.yml` 是什么？
6. 环境变量是什么？
7. 端口配置在哪里？
8. 数据库密码、JWT 密钥、模型 Key 应该放在哪里？

## 先用手机设置理解配置

你手机里的 App 通常有设置页面。

比如：

- 是否开启通知
- 使用深色模式还是浅色模式
- 语言是中文还是英文
- 是否允许定位
- 下载文件保存在哪里

这些都是配置。

代码不用改，但 App 行为会变。

后端项目也是一样。

比如 AgentX 可以配置：

- 后端端口是多少
- 数据库地址是什么
- 数据库用户名和密码是什么
- RabbitMQ 地址是什么
- 管理员默认密码是什么
- 模型服务商 API Key 是什么
- 文件保存在哪里

这些就是系统配置。

## 为什么配置不能全部写死在代码里

假设代码里直接写死：

```text
数据库密码 = 123456
模型 API Key = abcdefg
后端端口 = 8088
```

会有很多问题：

1. 不安全，密钥容易泄露。
2. 换环境很麻烦。
3. 本地、测试、生产环境配置不同。
4. 每次改配置都要重新改代码。

所以更好的做法是：

```text
代码负责逻辑。
配置文件负责可变化的参数。
```

## 什么是环境

项目通常会运行在不同环境里。

比如：

| 环境 | 说明 |
| --- | --- |
| 本地开发环境 | 开发者自己电脑 |
| 测试环境 | 测试人员验证功能 |
| 生产环境 | 真正给用户使用 |

不同环境配置可能不一样。

比如：

```text
本地数据库密码比较简单。
生产数据库密码必须很安全。
本地日志可以详细。
生产日志不能太啰嗦。
本地可以用测试支付。
生产要用真实支付。
```

这就是为什么配置要独立出来。

## 什么是 `.env`

`.env` 是环境变量配置文件。

你可以把它理解成：

```text
项目启动时要读取的一张配置清单。
```

比如里面可能有：

```text
SERVER_PORT=8088
DB_PASSWORD=agentx_pass
RABBITMQ_PASSWORD=guest
JWT_SECRET=your_secret
AGENTX_ADMIN_PASSWORD=admin123
```

每一行大概都是：

```text
配置名=配置值
```

## 什么是 `.env.example`

`.env.example` 是配置模板。

它告诉你：

```text
这个项目需要哪些配置项。
```

但它通常不是最终使用的配置。

你需要复制一份：

```powershell
Copy-Item .env.example .env
```

然后修改 `.env`。

生活类比：

```text
.env.example 是空白表格模板。
.env 是你真正填写后的表格。
```

## 为什么不直接改 `.env.example`

因为 `.env.example` 是模板。

模板通常要保留给别人参考。

真正本地运行时，应该改：

```text
.env
```

这样更清晰：

```text
.env.example：示例配置。
.env：当前机器实际配置。
```

## `.env` 里常见配置

AgentX 的 `.env` 里会有很多配置。

新手先重点看这些：

### 后端端口

```text
SERVER_PORT=8088
```

表示后端服务使用 8088 端口。

### 数据库配置

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
```

表示数据库在哪里、叫什么、用什么账号密码连接。

### RabbitMQ 配置

```text
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
```

表示消息队列服务怎么连接。

### JWT 密钥

```text
JWT_SECRET
```

用于登录 token 的安全签名。

这个在生产环境必须改成复杂值。

### 管理员默认密码

```text
AGENTX_ADMIN_PASSWORD
```

默认管理员密码。

学习阶段可以用默认值。

生产环境必须修改。

### 文件存储路径

```text
FILE_STORAGE_PATH
```

表示上传文件保存在哪里。

## 什么是环境变量

环境变量可以理解成：

```text
操作系统或容器提供给程序的一些配置值。
```

程序启动时，可以读取这些值。

比如：

```text
DB_PASSWORD=agentx_pass
```

后端启动时读取这个环境变量，就知道数据库密码是什么。

## Docker 和环境变量的关系

我们用 Docker 启动 AgentX 时，有一段参数：

```text
--env-file .env
```

意思是：

```text
启动容器时，把 .env 文件里的配置传给容器。
```

所以 `.env` 会影响 Docker 容器里的 AgentX。

这就是为什么我们启动容器前需要准备 `.env`。

## 什么是 `application.yml`

`application.yml` 是 Spring Boot 后端的配置文件。

路径：

```text
AgentX/src/main/resources/application.yml
```

它负责配置后端应用。

比如：

- 后端端口
- 数据库连接
- RabbitMQ 连接
- 日志级别
- 文件存储
- 第三方服务配置

你可以把它理解成：

```text
后端自己的设置文件。
```

## `.env` 和 `application.yml` 有什么区别

简单理解：

```text
.env 是外部启动配置。
application.yml 是后端内部配置。
```

在 Docker 场景里：

```text
.env 提供变量
  ↓
Docker 把变量传给容器
  ↓
Spring Boot 后端读取变量
  ↓
application.yml 使用这些变量
```

所以两者经常配合使用。

## 为什么有些配置看起来重复

你可能会看到：

```text
.env 里有数据库配置
application.yml 里也有数据库配置
```

这是正常的。

因为 `application.yml` 里可能写的是默认值或占位符。

`.env` 里的环境变量可以覆盖它。

生活类比：

```text
application.yml 像默认设置。
.env 像当前运行环境的实际设置。
```

## 什么是默认值

配置里可能会写：

```text
${DB_PASSWORD:postgres}
```

这表示：

```text
如果有 DB_PASSWORD，就用 DB_PASSWORD。
如果没有，就用 postgres。
```

冒号后面的就是默认值。

新手看到这种写法不要慌。

它的意思就是：

```text
优先用环境变量，没有就用默认值。
```

## 端口配置怎么理解

AgentX 常见端口：

| 服务 | 端口 |
| --- | --- |
| 前端 | 3000 |
| 后端 | 8088 |
| PostgreSQL | 5432 |
| RabbitMQ | 5672 |
| RabbitMQ 管理页 | 15672 |

端口就是服务的门牌号。

比如：

```text
http://localhost:3000
```

访问前端。

```text
http://localhost:8088/api/health
```

访问后端健康检查。

## 如果端口被占用怎么办

如果启动时报：

```text
port is already allocated
```

说明端口被占用了。

比如 3000 已经被别的软件占用。

解决方式：

1. 关闭占用端口的软件。
2. 或者修改配置，让服务使用别的端口。

不过新手阶段，优先不要乱改端口。

先检查是不是 AgentX 已经启动了：

```powershell
docker ps
```

## 密钥为什么重要

很多配置是敏感信息。

比如：

- 数据库密码
- JWT_SECRET
- 模型 API Key
- 支付密钥
- GitHub Token

这些不能随便公开。

如果泄露，别人可能：

- 登录你的系统
- 访问你的数据库
- 消耗你的模型额度
- 操作你的支付配置

所以真实项目中，密钥要妥善保存。

## 学习阶段可以用默认配置吗

可以。

本地学习阶段，使用默认管理员账号和默认数据库密码问题不大。

但如果部署到公网或生产环境，必须修改：

```text
AGENTX_ADMIN_PASSWORD
DB_PASSWORD
RABBITMQ_PASSWORD
JWT_SECRET
```

记住：

```text
本地学习可以简单。
生产环境必须安全。
```

## 模型 API Key 放在哪里

大模型服务商的 API Key 也属于敏感配置。

可能出现在：

- 后台模型服务商配置页面
- `.env`
- 数据库中的加密字段

不同项目设计不完全一样。

你现在先记住：

```text
API Key 是调用大模型的钥匙。
不能随便公开。
```

## 配置错误会导致什么问题

配置错了，系统可能出现：

### 数据库配置错

后端启动失败，或者登录失败。

### RabbitMQ 配置错

异步任务无法处理。

### 后端端口配置错

前端找不到后端。

### 模型 API Key 配错

Agent 无法正常回答。

### JWT_SECRET 配错

登录 token 可能无法验证。

## 怎么排查配置问题

新手按这个顺序：

```text
1. 看容器是否启动
2. 看前端是否能打开
3. 看后端健康检查
4. 看 docker logs
5. 看 .env
6. 看 application.yml
```

常用命令：

```powershell
docker ps
docker logs --tail 100 agentx
```

## 本课对应文件

根目录配置模板：

```text
E:\agentx\AgentX\.env.example
```

实际运行配置：

```text
E:\agentx\AgentX\.env
```

后端配置：

```text
E:\agentx\AgentX\AgentX\src\main\resources\application.yml
```

部署配置：

```text
E:\agentx\AgentX\deploy
```

前端配置相关：

```text
agentx-frontend-plus/lib/api-config.ts
agentx-frontend-plus/lib/http-client.ts
```

## 新手应该重点看哪些配置

先看：

```text
FRONTEND_PORT
BACKEND_PORT
SERVER_PORT
DB_PASSWORD
RABBITMQ_PASSWORD
JWT_SECRET
AGENTX_ADMIN_PASSWORD
NEXT_PUBLIC_API_BASE_URL
```

先不用深挖支付、GitHub、对象存储、模型高可用这些高级配置。

## 本课小结

这一课你要记住：

```text
配置决定系统怎么运行。
.env 是当前环境的启动配置。
.env.example 是配置模板。
application.yml 是 Spring Boot 后端配置。
环境变量可以覆盖默认配置。
端口是服务的门牌号。
密钥、密码、API Key 不能随便公开。
```

最重要的一句话：

```text
代码负责逻辑，配置负责变化。
```

## 课后练习

### 练习 1：找到 `.env`

找到：

```text
E:\agentx\AgentX\.env
```

观察里面有哪些配置项。

### 练习 2：找到 `.env.example`

找到：

```text
E:\agentx\AgentX\.env.example
```

比较它和 `.env` 是否类似。

### 练习 3：找到后端配置

找到：

```text
AgentX/src/main/resources/application.yml
```

先不用看懂所有内容，只找：

```text
server
datasource
rabbitmq
```

### 练习 4：解释环境变量

用自己的话回答：

```text
环境变量是什么？
```

建议答案：

```text
环境变量是程序启动时从外部读取的配置值。
```

### 练习 5：解释端口

用自己的话解释：

```text
3000 是什么？
8088 是什么？
5432 是什么？
```

建议答案：

```text
3000 是前端端口。
8088 是后端端口。
5432 是数据库端口。
```

## 下一课预告

第 11 课会进入 Agent 核心模型。

也就是：

- Agent 是什么
- Agent 有哪些字段
- 系统提示词是什么
- 欢迎语是什么
- 模型配置是什么
- 工具配置和知识库配置是什么
- Agent 和普通聊天有什么区别

从第 11 课开始，我们会正式进入 AgentX 最核心的业务模块。
