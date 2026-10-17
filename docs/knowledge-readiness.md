# 上传资料与可检索版本

知识库文件上传后会自动进入 OCR 与向量化流程。Web 知识库详情页显示每个文件的处理进度和失败原因，并在顶部汇总当前资料状态。

## 状态与版本

- `EMPTY`：没有文件。
- `PROCESSING`：至少一个文件仍在处理，不能确认新版本。
- `FAILED`：至少一个文件处理失败；接口返回可展示的 `failureReason`，文件进度接口也返回各自的原因。
- `READY`：当前文件全部完成处理，可以点击“确认可检索版本”。

确认版本会复制已处理的文件与语料，生成不可变快照，并返回稳定的版本 ID。空的初始 `0.0.1` 版本及尚未通过就绪检查的普通发布版本不会带有 `searchReady=true`。新上传或重新处理文件会改变当前状态，但不会修改此前创建的快照。Web 可用“查看快照”读取固定版本中的文件和语料预览。

全新部署使用 `docs/sql/01_init.sql`，其中已经包含这两个字段。现有数据库升级时，在启动新版服务前执行 `AgentX/src/main/resources/db/migration/V20261015007__knowledge_readiness.sql`；当前工程未配置自动数据库迁移器。

## 接口

以下接口均使用当前登录用户身份，并要求其拥有该知识库。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/api/rag/datasets/{datasetId}/readiness` | 当前状态、文件数、失败原因、最近就绪版本 ID |
| `GET` | `/api/rag/datasets/{datasetId}/files/progress` | 各文件状态与失败原因 |
| `POST` | `/api/rag/datasets/{datasetId}/ready-versions` | 全部文件就绪后创建快照 |
| `GET` | `/api/rag/datasets/{datasetId}/ready-versions/{versionId}` | 按稳定 ID 读取已就绪快照预览 |

创建版本前再次校验全部文件状态；若处理状态变化或快照没有可检索语料，创建失败且事务回滚。现有实时检索只返回处理完成的文件；未完成文件不会作为检索依据。

## 本地验证

后端：在 `AgentX` 目录执行 `./mvnw -Dtest=KnowledgeReadinessTest,KnowledgeReadinessHttpTest test spotless:check`。

前端：在 `agentx-frontend-plus` 目录执行 `npm ci --legacy-peer-deps --include=optional`、`npm run test:knowledge`、`npm run lint` 和 `npm run build`。
