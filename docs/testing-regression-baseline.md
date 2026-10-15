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

现有 `@SpringBootTest` 集成测试依赖 PostgreSQL、RabbitMQ、Docker 等外部服务。本次已移除容器集成测试中的永久等待循环，但这些测试仍需真实外部服务。本工作流只运行上述可独立复现的聊天回归测试；外部集成测试尚不能作为无服务的 PR 检查运行。
