# 目标运行：数据表与本地验证

目标运行使用 PostgreSQL 中的 `agent_runs` 和 `agent_run_steps` 保存权威状态。一个会话可包含多次运行；每次运行有独立的编号、目标、状态、结果和步骤。现有聊天 SSE 接口保持可用。

全新自部署环境使用 `docs/sql/01_init.sql` 创建这两张表。已有数据库须先备份，再执行增量 SQL：

```bash
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f AgentX/src/main/resources/db/migration/V20261015001__create_agent_runs.sql
```

仓库目前未自动执行 `db/migration` 目录中的 SQL，因此升级已有数据库时必须显式执行上述命令。两张表创建后，可在 Web 工作区选中会话，切换到“运行”视图，连续提交两个目标并刷新页面检查列表与详情。公开接口为：

- `POST /api/agents/sessions/{sessionId}/runs`，请求体 `{"goal":"目标内容"}`。
- `GET /api/agents/sessions/{sessionId}/runs`，读取会话运行列表。
- `GET /api/agents/runs/{runId}`，读取运行及步骤详情。

简单目标通过 Agent 工作区配置的模型连接执行。此阶段模型执行是同步的；提交请求结束后返回已完成或失败的状态。后续恢复与工作派发工单将沿用运行记录，增加跨重启继续处理能力。
