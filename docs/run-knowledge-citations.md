# 运行固定知识版本与引用

运行表单只列出当前 Agent 已关联的知识库，使用者可以选择本次运行使用的知识库。创建运行时，服务器会确认所选 ID 仍属于 Agent，并要求其拥有 `search_ready` 版本，再把该版本 ID 写入 `agent_runs.knowledge_version_id`。所选知识库未关联或没有可检索版本时，运行创建失败。没有传入知识库 ID 的旧 API 调用会按 Agent 的配置选择第一个可用的 `search_ready` 版本。之后的检索只读取固定版本的 `rag_version_documents` 和 `rag_version_files`，不会回到可变的原始数据集或实时混合检索。

运行详情中会返回 `knowledgeVersionId`。使用者可以调用：

```text
GET /agents/runs/{runId}/knowledge?query={目标中的检索问题}
GET /agents/runs/{runId}/knowledge/documents/{documentId}
```

检索结果的每条引用包含固定版本 ID、文档 ID、版本文件 ID、文件名、页码、内容和 `sourceUrl`。`sourceUrl` 是需要登录认证的后端 API 路径；Web 控制台使用运行范围内的引用接口读取全文。服务端会再次检查使用者权限与版本的 `search_ready` 状态。

当固定版本不存在、没有资料或没有命中证据时，结果返回 `hasEvidence=false`、空引用列表和明确的 `message`。`canPublish` 同时为 `false`；调用方不得把无证据结果自动提交到外部系统。

检索会先丢弃常见请求用语（例如“创建 GitHub Issue”），要求查询和同一资料片段都至少匹配两个不同的主题词；仅有“创建”“Issue”“项目”“仓库”等常用词命中不能产生引用，也不能打开外部写入审批。模型草拟的外部动作只有在该固定版本提供支持性引用时才会进入审批流程。引用会附加在不可变审批参数的正文中，详情页展示的正文就是批准后执行的正文。

## 全新部署与已有数据库

全新 PostgreSQL 部署会从 `docs/sql/01_init.sql` 创建字段。已有数据库使用 `AgentX/src/main/resources/db/migration/V20261016008__pin_run_knowledge_version.sql` 增加字段和索引；当前部署不会自动执行 Flyway 文件时，需要由运维在停机窗口手动执行该 SQL。
