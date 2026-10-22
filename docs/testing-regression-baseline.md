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

浏览器 job 保留现有全流程断言和重试规则；失败时上传仓库根的 `.playwright-results`，其中包括 Playwright trace 和页面错误上下文，方便区分页面行为失败与启动环境失败。此流程替换所有平台 HTTP 请求，只证明浏览器路径；真实模型、教材及 GitHub 的人工验收仍按本地验收指南执行。

## 2026-10-20 升级前复核

起点为主分支 `9bddb4540888ee60e668d388ef663658e49a4cf1`，不是沿用旧 CI 的成功结论。现有确定性后端集合运行 54 项，53 项通过；SSE 完整回复样例在本机并发编译期间超过 30 秒。停止浏览器并单独重跑后，该类 2 项测试均通过（2.7 秒），没有修改其超时或断言。

对空的专用 PostgreSQL 实际启用持久化测试，发现测试夹具遗漏既有 `agents`、`tools` 身份表，导致工具策略迁移的外键找不到 `tools`。夹具补齐这两个主键前置表，Run 表、约束和索引继续全部使用原有真实迁移。重跑真实恢复、并发取消和重复派发 2 项，以及迁移顺序 3 项，合计 **5 项通过、0 跳过**。因此新增 CI job 能在空库运行，不依赖用户部署中恰好已有的表。

浏览器历史 CI（run `39096058226`）首轮在等待审批时整条流程超过 30 秒，重试在“工具 & 知识库”标签点击后未激活。当前本机原样复核先遇到开发服务器 120 秒冷启动超时；独立预启动后，`/knowledge` 冷编译耗时 55.5 秒，超过既有页面测试 30 秒门槛。此环境失败不证明业务断言通过；不得把它记作真实 GitHub 闭环验收。失败 trace 由 CI 自动保存，后续须在可用环境重跑全部浏览器断言。

恢复后的空库验证还发现部署初始化 SQL 在执行追踪表上引用未创建的序列；这会让 PostgreSQL 初始化在到达 Run 表之前就停止。将这两列恢复为历史迁移采用的 `BIGSERIAL`，并补齐首阶段已约定的工具动作分类、Agent 动作选择、创建者预授权，以及 `tools`、`tool_versions`、`user_tools` 的 MCP 协议字段。专用新库只执行完整初始化 SQL，即可查询上述全部结构；随后真实持久化回归 **5 项通过、0 跳过**。CI 在运行增量迁移夹具前先执行同一部署 SQL 并检查这些字段，防止夹具掩盖 bootstrap 缺口。已有数据库仍按升级说明执行未应用的增量迁移，不能重放整份初始化 SQL。

原样预热流程在等待数据请求、文件上传和标签切换处均发生过超时。检查 Next 的 `.next/trace` 后，`webpack-invalidated-client` 的 `trigger` 连续指向 Playwright 在前端 `test-results` 中写入的 trace 和 screencast 文件：取证写入触发 HMR，再次渲染又产生取证帧，形成反馈循环。修复仅把 Playwright `outputDir` 移到前端目录之外的仓库根 `.playwright-results`，并同步 CI artifact 路径；保留 trace、所有页面断言及既有超时。

修复后同一份 E2E（测试文件未改动）在已有开发进程上 **7.6 秒通过**；再以 `CI=true` 让 Playwright 自行启动和关闭 Next，流程 **18.8 秒通过**、总命令 27.1 秒。未使用重试才通过，未修改 30 秒测试门槛、5 秒断言门槛或 120 秒启动门槛。`npm run lint` 同时通过既有基线，没有新增诊断。这些结果仍是平台 HTTP 替身支持的浏览器验收，不等于真实模型和 GitHub 写入验收。

合并教学选材集成分支后，以上确定性后端集合重新运行 **54 项通过、0 跳过**；再次在全新 PostgreSQL 库执行合并后的完整初始化 SQL，工具协议/审批结构和教学选材结构均可读取。沙盒内运行 Mockito 曾因 JDK 自附加被阻止而失败，该环境问题通过使用正常批准的测试权限解决，没有修改 Mockito、跳过测试或放宽检查。

## 教学与 PR 输入的真实 PostgreSQL HTTP 回归

`teaching-pr-http` 是独立的 CI job，在临时 pgvector/pg15 中运行 `TeachingSelectionHttpTest`、`WordSourceHttpTest`、`TeachingOutlineHttpTest`、`PrInputHttpTest` 与 `PrInputPostgresHttpIntegrationTest`。它显式启用 `TEACHING_INTEGRATION` 与 `PR_INPUT_POSTGRES_URL`，要求五份 JUnit 报告均有测试且没有失败、错误或跳过，防止因环境变量缺失默认 skip 而假绿。

测试对大纲/选材/PR 快照持久化使用真实数据库；模型、GitHub 外部读取使用受控响应。该 job 不替代完整生产身份认证、浏览器、真实课本/模型与真实 PR 的 #36/#41 验收。Word 测试使用实际 DOCX、本地文件存储及生产 FileRecorder/POI；OCR 等后续 HTTP 测试落地后需要加入相同的执行及证据检查清单。
