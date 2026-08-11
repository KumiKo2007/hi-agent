# 第 2 课：本地环境与项目启动（小白版）

## 这节课先解决什么问题

上一课我们知道了 AgentX 是一个智能体平台。

这一课只做一件事：

```text
把 AgentX 在你的电脑上跑起来，然后能用浏览器打开。
```

你暂时不需要理解所有技术细节。你只需要先知道：

- Docker 是什么
- 为什么要用 Docker
- 怎么确认项目有没有启动成功
- 启动后应该打开哪个网址
- 出问题时先看哪里

## 先用生活例子理解

假设 AgentX 是一家餐厅。

这家餐厅要正常营业，需要：

- 前台：给顾客点餐
- 厨房：真正做菜
- 仓库：保存食材
- 传菜员：把菜送出去
- 收银台：记录订单和付款

放到 AgentX 里，大概对应：

| 餐厅里的东西 | AgentX 里的东西 | 作用 |
| --- | --- | --- |
| 前台 | 前端页面 | 你在浏览器里看到的界面 |
| 厨房 | 后端服务 | 真正处理业务逻辑 |
| 仓库 | 数据库 | 保存用户、Agent、聊天记录 |
| 传菜员 | 消息队列 | 处理一些异步任务 |
| 外卖平台 | 大模型服务 | 生成 AI 回复 |

所以 AgentX 不是一个单独的软件窗口，它背后其实有好几个服务一起工作。

## Docker 是什么

你可以把 Docker 理解成：

```text
一个“软件打包盒子”。
```

正常安装一个复杂项目，你可能要分别安装：

- Java
- Node.js
- PostgreSQL 数据库
- RabbitMQ 消息队列
- 各种依赖
- 各种配置

这对新手很不友好。

Docker 的作用就是把这些东西尽量装进一个“盒子”里。你只要启动这个盒子，里面的服务就一起跑起来。

这就是为什么我们先用 Docker。

## 你现在采用的启动方式

我们现在用的是：

```text
一体化 Docker 镜像启动
```

意思是：

```text
前端 + 后端 + 数据库 + RabbitMQ
都被打包在一个 AgentX 容器里。
```

你不需要分别启动很多东西。

## 先认识几个网址

项目启动后，你主要会用到这些地址：

| 地址 | 作用 |
| --- | --- |
| http://localhost:3000 | AgentX 前端页面 |
| http://localhost:3000/admin | 后台管理系统 |
| http://localhost:8088/api/health | 后端健康检查 |
| http://localhost:15672 | RabbitMQ 管理页面 |

这里的 `localhost` 表示：

```text
你自己的电脑。
```

所以：

```text
http://localhost:3000
```

意思就是：

```text
打开你自己电脑上 3000 端口的 AgentX 页面。
```

## 什么是端口

端口可以理解成一栋楼里的不同房间号。

你的电脑是一栋楼：

```text
localhost
```

不同服务住在不同房间：

```text
3000 号房间：前端页面
8088 号房间：后端 API
5432 号房间：数据库
5672 号房间：RabbitMQ
15672 号房间：RabbitMQ 管理页面
```

所以访问不同端口，就相当于去找不同服务。

## 启动前要确认 Docker 已经运行

先打开 Docker Desktop。

如果 Docker Desktop 已经正常运行，通常会显示类似：

```text
Docker Desktop is running
```

也可以打开 PowerShell，输入：

```powershell
docker info
```

如果能看到一大段 Docker 信息，说明 Docker 正常。

如果报错，通常说明 Docker Desktop 没打开，或者还没启动完成。

## 启动 AgentX

如果容器已经创建过，只需要执行：

```powershell
docker start agentx
```

如果你不确定项目有没有在运行，可以先执行：

```powershell
docker ps -a
```

它会列出电脑上已有的 Docker 容器。

如果看到：

```text
agentx
```

并且状态里有：

```text
Up
```

说明项目已经启动。

## 第一次启动时使用的命令

第一次创建并启动 AgentX 容器时，用的是类似下面的命令：

```powershell
docker run -d --name agentx `
  -p 3000:3000 `
  -p 8088:8088 `
  -p 5432:5432 `
  -p 5672:5672 `
  -p 15672:15672 `
  --env-file .env `
  -v agentx-data:/var/lib/postgresql/data `
  -v agentx-storage:/app/storage `
  ghcr.io/lucky-aeon/agentx:latest
```

这段命令不用现在背下来。

你只要理解它做了几件事：

1. 创建一个叫 `agentx` 的容器。
2. 把容器里的前端映射到电脑的 `3000` 端口。
3. 把容器里的后端映射到电脑的 `8088` 端口。
4. 把数据库、RabbitMQ 也映射出来。
5. 使用 `.env` 文件里的配置。
6. 把数据保存到 Docker volume 里，防止容器重启后数据丢失。

## 怎么判断启动成功

启动成功后，执行：

```powershell
docker ps
```

如果看到类似：

```text
agentx   Up ...   healthy
```

说明容器正常运行。

其中：

- `Up` 表示正在运行
- `healthy` 表示健康状态正常

然后打开浏览器：

```text
http://localhost:3000
```

如果能看到 AgentX 页面，就说明前端成功。

再打开：

```text
http://localhost:8088/api/health
```

如果看到正常返回，说明后端成功。

## 登录账号

默认管理员账号：

```text
admin@agentx.ai
admin123
```

默认测试账号：

```text
test@agentx.ai
test123
```

后台管理系统地址：

```text
http://localhost:3000/admin
```

如果要进入后台，建议使用管理员账号登录。

## 停止项目

如果你想停止 AgentX，可以执行：

```powershell
docker stop agentx
```

停止后，再访问：

```text
http://localhost:3000
```

页面就打不开了。

## 再次启动项目

下次想继续学习时，只需要：

```powershell
docker start agentx
```

然后打开：

```text
http://localhost:3000
```

这就是 Docker 的方便之处。

## 查看项目日志

如果页面打不开，可以看日志：

```powershell
docker logs --tail 100 agentx
```

这条命令的意思是：

```text
查看 agentx 容器最近 100 行日志。
```

如果你想持续观察日志，可以用：

```powershell
docker logs -f agentx
```

按 `Ctrl + C` 可以退出日志查看。

## 常见问题 1：Docker 没启动

现象：

```text
Cannot connect to the Docker daemon
```

意思是：

```text
PowerShell 找不到 Docker 后台服务。
```

解决：

1. 打开 Docker Desktop。
2. 等它完全启动。
3. 再执行命令。

## 常见问题 2：端口被占用

现象可能是：

```text
port is already allocated
```

意思是：

```text
这个端口已经被别的软件占用了。
```

比如 `3000` 被占用，前端页面就启动不了。

可以先看看是否已经有 AgentX 在运行：

```powershell
docker ps
```

如果已经有 `agentx` 在运行，就不用重复启动。

## 常见问题 3：页面打不开

按这个顺序检查：

### 第一步：看容器是否运行

```powershell
docker ps
```

有没有 `agentx`？

### 第二步：看状态是否健康

有没有：

```text
healthy
```

### 第三步：看日志

```powershell
docker logs --tail 100 agentx
```

### 第四步：确认访问地址

前端页面是：

```text
http://localhost:3000
```

不要写成：

```text
https://localhost:3000
```

这里用的是 `http`，不是 `https`。

## 常见问题 4：后端根路径返回 404

你访问：

```text
http://localhost:8088
```

可能会看到 404。

这不一定是错误。

因为后端不是网页，它主要提供 API。应该访问：

```text
http://localhost:8088/api/health
```

只要这个地址正常，就说明后端正常。

## 你需要记住的最少命令

作为新手，先记住这几个就够了：

### 查看 Docker 容器

```powershell
docker ps
```

### 查看所有容器，包括停止的

```powershell
docker ps -a
```

### 启动 AgentX

```powershell
docker start agentx
```

### 停止 AgentX

```powershell
docker stop agentx
```

### 查看日志

```powershell
docker logs --tail 100 agentx
```

## 本课你真正要理解的东西

你不需要马上理解 Docker 的全部原理。

现在只需要记住：

```text
Docker 帮我们把复杂环境打包起来。
AgentX 运行在一个叫 agentx 的容器里。
浏览器访问 localhost:3000 就能打开项目。
后台管理在 localhost:3000/admin。
后端健康检查在 localhost:8088/api/health。
```

## 本课小结

这一课我们完成了从“项目代码”到“浏览器页面”的第一步。

你可以把整个运行过程理解成：

```text
打开 Docker Desktop
  ↓
启动 agentx 容器
  ↓
容器里自动启动前端、后端、数据库、RabbitMQ
  ↓
浏览器打开 http://localhost:3000
  ↓
登录并体验系统
```

对新手来说，能做到这一步已经很关键了。

后面学习源码时，你会慢慢知道：

- 前端页面在哪里
- 后端接口在哪里
- 数据库表在哪里
- 聊天请求怎么从前端走到后端
- AI 回复是怎么生成并返回的

## 课后练习

### 练习 1：打开前端页面

打开：

```text
http://localhost:3000
```

确认页面能正常显示。

### 练习 2：打开后台管理

打开：

```text
http://localhost:3000/admin
```

使用管理员账号登录：

```text
admin@agentx.ai / admin123
```

### 练习 3：检查后端健康状态

打开：

```text
http://localhost:8088/api/health
```

确认后端能正常响应。

### 练习 4：执行 Docker 命令

在 PowerShell 里执行：

```powershell
docker ps
```

找到 `agentx` 这一行，并观察它的状态。

### 练习 5：停止并重新启动

先停止：

```powershell
docker stop agentx
```

再启动：

```powershell
docker start agentx
```

然后重新打开：

```text
http://localhost:3000
```

如果页面能再次打开，说明你已经掌握了最基本的启动流程。

## 下一课预告

第 3 课我们会从用户视角体验系统。

下一课会做这些事：

- 登录 AgentX
- 创建第一个 Agent
- 配置模型
- 发起一次对话
- 观察页面上发生了什么
- 初步理解“前端页面”和“后端接口”的关系

从第 3 课开始，我们会真正使用 AgentX，然后慢慢回到源码里找对应的代码。
