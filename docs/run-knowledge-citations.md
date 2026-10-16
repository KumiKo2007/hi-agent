# 运行固定知识版本与引用

运行创建时，会根据 Agent 的知识库配置选择最近一个 `search_ready` 版本，并把版本 ID 写入 `agent_runs.knowledge_version_id`。之后的检索只读取该版本的 `rag_version_documents` 和 `rag_version_files`，不会回到可变的原始数据集或实时混合检索。

运行详情中会返回 `knowledgeVersionId`。使用者可以调用：

```text
GET /agents/runs/{runId}/knowledge?query={目标中的检索问题}
GET /agents/runs/{runId}/knowledge/documents/{documentId}
```

检索结果的每条引用包含固定版本 ID、文档 ID、版本文件 ID、文件名、页码、内容和 `sourceUrl`。`sourceUrl` 是需要登录认证的后端 API 路径；Web 控制台使用运行范围内的引用接口读取全文。服务端会再次检查使用者权限与版本的 `search_ready` 状态。

当固定版本不存在、没有资料或没有命中证据时，结果返回 `hasEvidence=false`、空引用列表和明确的 `message`。`canPublish` 同时为 `false`；调用方不得把无证据结果自动提交到外部系统。

## 全新部署与已有数据库

全新 PostgreSQL 部署会从 `docs/sql/01_init.sql` 创建字段。已有数据库使用 `AgentX/src/main/resources/db/migration/V20261016008__pin_run_knowledge_version.sql` 增加字段和索引；当前部署不会自动执行 Flyway 文件时，需要由运维在停机窗口手动执行该 SQL。
