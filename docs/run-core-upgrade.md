# 目标运行：数据表与本地验证

目标运行使用 PostgreSQL 中的 `agent_runs` 和 `agent_run_steps` 保存权威状态。一个会话可包含多次运行；每次运行有独立的编号、目标、状态、结果和步骤。现有聊天 SSE 接口保持可用。

全新自部署环境使用 `docs/sql/01_init.sql` 创建这两张表。已有数据库须先备份，再执行增量 SQL：

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015001__create_agent_runs.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015004__run_delivery_lease.sql
```

仓库目前未自动执行 `db/migration` 目录中的 SQL，因此升级已有数据库时必须显式执行上述命令。两张表创建后，可在 Web 工作区选中会话，切换到“运行”视图，连续提交两个目标并刷新页面检查列表与详情。公开接口为：

- `POST /api/agents/sessions/{sessionId}/runs`，请求体 `{"goal":"目标内容"}`。
- `GET /api/agents/sessions/{sessionId}/runs`，读取会话运行列表。
- `GET /api/agents/runs/{runId}`，读取运行及步骤详情。
- `POST /api/agents/runs/{runId}/cancel`（或 `DELETE /api/agents/runs/{runId}`），由运行所有者取消未完成运行。

提交目标后立即返回 `PENDING`。RabbitMQ 派发运行编号，工作者通过 PostgreSQL 的租约领取工作；启动时和定期扫描会重新派发漏发或租约过期的运行。重复消息不会覆盖已完成结果，浏览器刷新后从 `GET` 接口读取同一个运行。部署时先执行 SQL，再启动使用新版代码的服务。

取消请求按运行所有者和未完成状态原子更新为 `CANCELLED`，同时关闭仍未完成的步骤并清理租约。后续队列消息不会再次领取该运行；已经领取的工作者也不能提交完成或失败结果。

恢复集成测试使用专用 PostgreSQL 数据库和 RabbitMQ 实例。设置 `RUN_DELIVERY_INTEGRATION=true`，可用 `RUN_TEST_DB_URL` 与 `RUN_TEST_RABBIT_PORT` 指定地址，然后在 `AgentX` 运行 `./mvnw -Dtest=RunPostgresRabbitIntegrationTest test`。测试通过公开运行接口提交目标，经过真实数据库与 RabbitMQ，验证租约过期恢复、重复消息和两个工作者的竞争。
