# 目标运行：数据表与本地验证

目标运行使用 PostgreSQL 中的 `agent_runs` 和 `agent_run_steps` 保存权威状态。一个会话可包含多次运行；每次运行有独立的编号、目标、状态、结果和步骤。运行还会固定用户选择的知识版本与目标仓库。现有聊天 SSE 接口保持可用。

全新自部署环境使用 `docs/sql/01_init.sql` 创建基础表。已有数据库须先备份，再按版本顺序执行运行功能所需的增量 SQL：

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015001__create_agent_runs.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015002__create_tool_action_policies.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015004__run_delivery_lease.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015007__knowledge_readiness.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015011__create_github_connections.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261016001__pin_run_agent_version.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261016008__pin_run_knowledge_version.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017001__add_mcp_transport_type.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017002__persist_run_approvals.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017003__add_creator_approval_policies.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017004__persist_approved_external_resource.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017005__audit_run_reconciliation.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017006__add_reconciliation_evidence_references.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017007__pin_run_target_repository.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261017008__add_run_timeline_events.sql
```

仓库目前未自动执行 `db/migration` 目录中的 SQL，因此升级已有数据库时必须显式执行尚未应用的迁移，并保持上述顺序。该序列要求 `docs/sql/01_init.sql` 创建的基础表已经存在；`V20261017002` 保持原审批表结构，后续 reconciliation 字段和表由 `V20261017005` 增量添加，证据引用字段由 `V20261017006` 添加，运行目标仓库字段由 `V20261017007` 添加，安全事件时间线由 `V20261017008` 添加。不要对已应用的非幂等迁移重复执行。两张运行表创建后，可在 Web 工作区选中会话，切换到“运行”视图，连续提交两个目标并刷新页面检查列表与详情。公开接口为：

- `POST /api/agents/sessions/{sessionId}/runs`，请求体 `{"goal":"目标内容"}`。
- `GET /api/agents/sessions/{sessionId}/runs`，读取会话运行列表。
- `GET /api/agents/runs/{runId}`，读取运行、步骤、按追加顺序排列的安全时间线、模型 token 汇总与有限的 `nextAction`。
- `POST /api/agents/runs/{runId}/cancel`（或 `DELETE /api/agents/runs/{runId}`），由运行所有者取消未完成运行。

提交目标后立即返回 `PENDING`。RabbitMQ 派发运行编号，工作者通过 PostgreSQL 的租约领取工作；启动时和定期扫描会重新派发漏发或租约过期的运行。重复消息不会覆盖已完成结果，浏览器刷新后从 `GET` 接口读取同一个运行。部署时先执行 SQL，再启动使用新版代码的服务。

Web 的“运行”表单会列出 Agent 已关联的知识库和当前 GitHub 连接已授权的仓库。选择仓库后，该 owner/repo 会由服务器重新验证并固定到运行记录；模型提出的写动作必须再次匹配该仓库。连接已撤销或仓库不在授权范围时，运行不会创建。API 也接受可选字段 `knowledgeBaseId` 与 `targetRepository`，例如 `{"goal":"...","knowledgeBaseId":"kb-id","targetRepository":"owner/repo"}`。知识库必须已关联到当前 Agent，并且有 `search_ready` 版本；目标仓库必须属于当前用户的授权连接。

外部写入审批展示服务器校验过的目标仓库、完整参数和资料引用。资料引用附加在审批正文中；批准后执行的参数与审批预览一致。审批 DTO 会保留可审阅的标题、正文、仓库和引用，同时隐藏 credential、header、cookie 字段；数据库中的不可变批准参数不变。固定知识版本没有足够具体的匹配依据时，不会创建写入审批草稿，也不会调用 MCP 写动作。成功结果只有在 MCP 返回可验证的同仓库 GitHub Issue URL 或 Issue 编号时才保存为链接；无法确认目标时运行进入结果核对状态。

时间线使用有限事件类型和显式元数据 DTO，不保存提示词、完整资料正文、工具参数、header 或任意模型/工具输出。模型用量汇总仅统计提供方返回的输入/输出 token；只要有一次调用缺失用量，汇总会显示不完整。模型调用毫秒数不包括审批或结果核对等待，也不代表统一货币成本。失败事件包含运行和步骤标识，可用于定位失败发生在哪个运行步骤。

取消请求按运行所有者和未完成状态原子更新为 `CANCELLED`，同时关闭仍未完成的步骤并清理租约。后续队列消息不会再次领取该运行；已经领取的工作者也不能提交完成或失败结果。

恢复集成测试使用专用 PostgreSQL 数据库和 RabbitMQ 实例。设置 `RUN_DELIVERY_INTEGRATION=true`，可用 `RUN_TEST_DB_URL` 与 `RUN_TEST_RABBIT_PORT` 指定地址，然后在 `AgentX` 运行 `./mvnw -Dtest=RunPostgresRabbitIntegrationTest test`。测试通过公开运行接口提交目标，经过真实数据库与 RabbitMQ，验证租约过期恢复、重复消息和两个工作者的竞争。
