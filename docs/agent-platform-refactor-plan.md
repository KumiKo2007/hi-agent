# hi Agent 重构方案（实施中）

基于 `master` 的 `1ef0f3e` 和 2026-10-14 的设计访谈。首张实施工单 #2 已建立对话回归基线；其余进度以 GitHub Issues #3–#21 为准。本文定义首阶段的产品闭环、架构边界与验收方式。术语见 [GLOSSARY.md](../GLOSSARY.md)，已确认的长期决策见 [docs/adr](./adr)。

## 目标与首个闭环

hi Agent 面向自行部署平台的开发者和小团队。保留自然语言配置 Agent 的方式和现有 Java/Spring Boot、Next.js 技术栈。首阶段证明一个单 Agent 能可靠完成真实工作：

1. 创建者在 Web 控制台配置 Agent、模型、知识库、MCP 服务与审批策略。
2. 使用者上传项目资料，连接自己的 GitHub 账号并选择可写仓库，在会话中提出目标。
3. 平台创建独立运行，固定 Agent 定义、工具目录与参数结构、知识版本；Agent 检索资料，生成带可核查依据的 GitHub Issue 草稿。
4. 平台按明确的工具动作策略决定是否暂停审批。默认所有外部写入需要确认；创建者可逐项预授权，使用者可对本次运行收紧，但不能放宽创建者的策略或 GitHub 仓库授权。
5. 执行后显示 Issue 链接、结果与逐步轨迹。审批等待、失败和进程重启后可继续。对结果不明的写入先自动核对；只有明确证据表明动作未执行时才自动重试，仍无法确认时暂停待核对。

首阶段只做用户发起的单 Agent 运行。暂缓定时自动触发、多 Agent/A2A、多模态、计费和工具市场的新功能；默认导航与介绍只展示已验证的能力，未完成能力明确标注。模型接入先稳定现有 OpenAI 兼容协议与 Anthropic。

## 现状与必须解决的缺口

| 领域 | 已验证现状 | 首阶段处理 |
| --- | --- | --- |
| 对话执行 | `ConversationAppService` 经 `MessageHandlerFactory` 和 LangChain4j `AiServices` 处理聊天，工具由 MCP provider 自动调用。 | 将模型决策与工具执行分开，让运行器在工具调用前持久记录、检查权限并在需要时暂停。 |
| 运行与追踪 | 现有追踪按 `sessionId` 汇总，同一会话中的多次目标无法独立追踪。 | 新建 Run 与 Step 状态模型，将追踪、审批、评估和恢复绑定到 Run。 |
| 中断与恢复 | 当前中断主要关闭进程内 SSE；审批暂停和持久检查点尚不存在。 | 持久化运行状态，修复中断入口的会话归属检查，区分取消运行与关闭浏览器连接。 |
| MCP | 当前使用基于 SSE 的传输、网关 API Key 与 LangChain4j fork 的 SNAPSHOT 依赖。 | 新连接以 Streamable HTTP 为目标，旧 SSE 过渡兼容；用可替换适配层隔离具体 SDK 和传输。 |
| RAG | 知识库已有版本概念，但实时文档查询仍有占位返回。 | 首个闭环使用上传资料的固定版本，并记录检索依据；实时同步另列后续任务。 |
| 质量门槛 | PR CI 只检查 Java 格式，主对话/MCP/追踪缺少自动测试。 | 为运行状态、安全、恢复和 UI 闭环建立自动测试及评测门槛。 |

证据：[对话主链](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/AgentX/src/main/java/org/xhy/application/conversation/service/ConversationAppService.java#L135-L157)、[Run 缺口](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/AgentX/src/main/java/org/xhy/domain/trace/service/AgentExecutionTraceDomainService.java#L34-L54)、[中断实现](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/AgentX/src/main/java/org/xhy/application/conversation/service/ChatSessionManager.java#L91-L132)、[MCP 客户端](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/AgentX/src/main/java/org/xhy/application/conversation/service/message/agent/AgentToolManager.java#L42-L65)、[RAG 占位](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/AgentX/src/main/java/org/xhy/domain/rag/service/management/RagDataAccessDomainService.java#L184-L190)、[当前 CI](https://github.com/KumiKo2007/hi-agent/blob/1ef0f3ea4f46e794db7d0cbb857f07f08ace04e9/.github/workflows/code-format-check.yml#L3-L28)。

## 目标架构

- **运行状态**：PostgreSQL 是 Run、Step、审批请求、工具调用意图与结果的权威记录。RabbitMQ 只负责派发；重复消息必须安全地落在同一个运行状态上。运行状态包括 `PENDING`（待执行）、`RUNNING`（运行中）、`WAITING_APPROVAL`（待审批）、`WAITING_RECONCILIATION`（待核对）、`WAITING_SCHEMA_REVIEW`（工具结构待核对）、`COMPLETED`（完成）、`FAILED`（失败）和 `CANCELLED`（取消）。冻结的工具动作参数结构与当前登记或远端 MCP 结构不一致时，运行持久化进入 `WAITING_SCHEMA_REVIEW`，保存安全的可见原因并停止工具调用，等待检查动作定义后重新运行。工作线程从持久记录恢复，不从 SSE 连接恢复。
- **工具边界**：模型提出工具动作，平台的 Tool Broker 执行权限检查、资源范围检查、审批、参数固定、审计与实际调用。MCP 服务和具体工具动作分开建模。工具服务的元数据不能单独决定写入权限；平台保存明确的动作分类和预授权。
- **外部身份**：使用者授权自己的 GitHub 身份，授权资源限定到所选仓库。密钥不进入模型上下文或普通执行日志。审批策略与外部账户权限都必须允许，写入才可发生。
- **恢复与去重**：写操作前保存意图和唯一动作标识；成功后保存外部资源 ID。队列重投或进程重启先读取已有结果。若外部结果不明，先按动作标识查询外部系统；查不到不等于证明没有创建，缺少明确证据时进入待核对状态，禁止盲目重复创建。
- **知识与评估**：运行固定知识版本，Issue 草稿保存引用的资料来源。轨迹按 Run 记录关键步骤、审批决定、工具结果、模型用量和失败原因；公开给使用者的内容先去除凭据等敏感信息。评测样例同时检查内容依据和是否越权写入。

当前 MCP 规范已以无状态请求为核心，但官方 Java SDK 的稳定线与规范版本存在时间差。首阶段以稳定实现的 Streamable HTTP 互通为门槛，具体 SDK 版本以实际兼容性测试决定，不把“最新规范号”本身当作验收项。[MCP 规范](https://modelcontextprotocol.io/specification/2026-07-28) · [Java SDK 路线图](https://github.com/modelcontextprotocol/java-sdk/blob/main/ROADMAP.md)

远程 MCP 服务的实现可能在运行期间自行更新；平台能固定已登记的工具描述与参数结构，不能冻结第三方服务代码。执行前若发现接口与固定结构不兼容，运行应暂停并报告，而不是对变化后的动作继续执行。

## 实施顺序

1. **建立可运行基线**：补齐 JDK/前端依赖，运行后端测试与前端 lint；修复中断入口的归属检查；在 PR CI 中加入构建、测试和 lint。统一 README 与项目源码的 MIT 许可声明，同时保留第三方文件原有许可头。
2. **拆出 Run 核心**：加入 Run/Step 的数据库迁移、状态转换和恢复测试；将执行追踪从会话级改为运行级；固定 Agent、工具、知识版本。旧会话 API 可按新模型适配，现有线上数据不构成迁移约束。
3. **接入受控工具调用**：从自动 MCP 工具执行中抽出 Tool Broker；实现创建者策略、使用者收紧、按仓库授权、审批暂停/恢复、写入意图与结果核对。通过适配层接入 Streamable HTTP，并保留旧 SSE 迁移通道。
4. **完成示例闭环**：上传项目资料、检索并引用依据、生成 Issue 草稿、执行审批和官方 GitHub MCP 工具调用；在 Web 控制台展示运行步骤、异常和结果。
5. **收紧发布门槛**：增加端到端和故障注入测试、版本化评测样例、自部署验收与文档；隐藏未验证功能入口。每个 PR 保持单一主题和明确回归步骤。

## 首阶段验收

- 在全新自部署环境中，使用者能从 Web 控制台完成配置、资料上传、GitHub 连接、发起运行、审批与查看 Issue/轨迹。
- 未授权仓库、被拒绝的动作、未预授权的写操作均不能产生外部写入；使用者不能放宽创建者的审批策略。
- 审批等待跨重启保留；重复队列消息和进程中断不自动重复创建 Issue；结果不明的写入要么核对成功，要么停在待核对状态。
- 自动测试覆盖状态转换、授权、审批、恢复、去重、MCP 适配与 Web 主流程；评测样例至少覆盖资料引用、Issue 内容、工具选择和失败处理，并记录通过率、成本与耗时。
- 后端测试、前端 lint/构建、端到端测试和安全性质测试在 PR CI 中通过。真实 GitHub 写入只在明确授权的测试仓库中验收。

## 已知实施约束

本机后端测试可在 JDK 17 容器中运行，但完整旧套件依赖 Docker socket 等外部服务；前端基线已有自动测试、lint 和构建命令，详见 [对话回归基线](./testing-regression-baseline.md)。GitHub 官方创建 Issue 的 REST 与 MCP 文档未提供创建幂等键，不能以盲目自动重试承诺不重复写入。[REST 文档](https://docs.github.com/en/rest/issues/issues#create-an-issue) · [官方 MCP Server](https://github.com/github/github-mcp-server)
