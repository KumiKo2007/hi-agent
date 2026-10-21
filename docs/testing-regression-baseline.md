# 对话回归基线

## 本地复现

需安装 JDK 17、Node.js 20 和 npm。后端测试使用内存中的确定性模型，通过现有 `POST /agents/sessions/chat` 接口发送一条消息，逐条验证 SSE 内容与完成事件，并验证空消息被拒绝；不需要数据库、Docker、模型密钥或其他外部服务。

```bash
cd AgentX
./mvnw -B -ntp -Dtest=PortalChatRegressionTest -DforkedProcessTimeoutInSeconds=60 test
```

```bash
cd agentx-frontend-plus
npm ci --legacy-peer-deps --include=optional
npm run test:chat
npm run lint
npm run build
```

PR 中的 `Conversation regression baseline` 工作流运行同样的命令，任一步失败都会让对应检查失败。现有 Java 格式检查继续由 `Java Code Format Check` 工作流执行。

前端测试在模拟浏览器中点击现有聊天面板的发送按钮，验证请求路径和正文，并消费分块的 SSE 回复直到页面显示结果。前后端测试共用 `AgentX/src/test/resources/contracts/chat-sse.json` 事件样例：后端验证真实 HTTP 响应的关键事件字段，前端用同一份事件生成 SSE 并驱动页面。两端分别使用确定性的模型或网络替身，不启动完整部署环境。

## 现有前端质量债

新加入的 ESLint 配置会检查 Next.js、React 和 TypeScript 规则。首次完整扫描发现 **1068 个既有错误和 91 个既有警告**，已记录在 `agentx-frontend-plus/lint-baseline.json`。`npm run lint` 会扫描整个前端（含测试），按文件、规则、严重程度、消息与源代码统计同类诊断；新增类别或数量会使检查失败，旧问题移动行列位置不会因此误报。解决旧问题后，可审查变更并运行 `node scripts/check-lint.mjs --update` 缩小基线。`npm run lint:strict` 展示所有未解决的诊断，目前预期失败。

前端还有既存 TypeScript 错误，当前 `next.config.mjs` 在构建时跳过类型和 lint 检查。`npm run build` 成功不代表类型检查通过；可用 `npx tsc --noEmit` 查看类型错误。后续工单应清理这些错误，再移除构建配置中的跳过项。

现有 `@SpringBootTest` 集成测试依赖 PostgreSQL、RabbitMQ、Docker 等外部服务。本次已移除容器集成测试中的永久等待循环，但这些测试仍需真实外部服务。工作流中的 `backend` 运行 HTTP/SSE、审批、所有者权限、知识版本固定和重复派发的确定性回归；这些测试中存在内存存储与外部替身，不能据此声称真实部署闭环通过。独立 `run-persistence` job 使用专用 PostgreSQL 和 RabbitMQ 验证恢复及重复派发，不复用部署中的数据库、队列或凭据。


## Run 持久化与浏览器证据

`run-persistence` job 显式设置 `RUN_DELIVERY_INTEGRATION=true`，因此启用真实数据库和队列测试；没有此变量时，该测试会被 JUnit 跳过。测试的所有者 HTTP 行为、租约过期恢复、工作者重建及重复派发经过真实 PostgreSQL/RabbitMQ，但 Session 和模型仍是确定性替身，不涵盖真实 GitHub 写入。测试报告单独作为 CI artifact 保存。

在专用、可丢弃的 PostgreSQL 数据库及 RabbitMQ 实例就绪后，可在 `AgentX` 运行：

```bash
RUN_DELIVERY_INTEGRATION=true \
RUN_TEST_DB_URL=jdbc:postgresql://localhost:55432/agentx_test \
RUN_TEST_RABBIT_PORT=55672 \
./mvnw -B -ntp -Dtest=RunPostgresRabbitIntegrationTest,RunMigrationSequenceTest -DforkedProcessTimeoutInSeconds=60 test
```

数据库测试会创建运行相关表，RabbitMQ 测试会清空专用运行队列；只能指向隔离的测试实例。默认数据库账号/密码为测试夹具中的 `postgres`，RabbitMQ 为 `guest`。

浏览器 job 保留现有全流程断言和重试规则；失败时上传 `test-results`，其中包括 Playwright trace 和页面错误上下文，方便区分页面行为失败与启动环境失败。此流程替换所有平台 HTTP 请求，只证明浏览器路径；真实模型、教材及 GitHub 的人工验收仍按本地验收指南执行。
