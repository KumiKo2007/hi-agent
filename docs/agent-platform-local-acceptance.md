# 首阶段 Agent 平台本地启动与验收

本指南用于在全新本地环境复现“配置 Agent、上传资料、连接 GitHub、审批并创建 Issue”的闭环。浏览器自动化使用本地 API 模拟；人工验收只对操作者拥有的专用测试仓库写入。

## 启动全新本地环境

准备 Docker Desktop（含 Docker Compose v2）、Git、OpenSSL 和可用的模型服务凭据。克隆仓库后运行：

```bash
cd deploy
cp .env.local.example .env
```

编辑 `deploy/.env`：

- 将 `JWT_SECRET` 替换为 `openssl rand -hex 32` 生成的随机值。
- 将 `HI_AGENT_GITHUB_TOKEN_KEY` 替换为 `openssl rand -base64 32` 生成的值。此密钥必须保持稳定，否则服务无法解密已保存的 GitHub 连接。
- 本机默认前端地址为 `http://localhost:3000`，API 地址为 `http://localhost:8088/api`。若修改端口，需同步更新 `NEXT_PUBLIC_API_BASE_URL`。
- `GITHUB_TOKEN` 保持空值。验收使用者的 PAT 应在 Web 控制台的“GitHub 连接”页面单独提交。
- 默认测试账号仅用于本地开发：`test@agentx.ai` / `test123`。请勿将开发账号或模板密钥用于生产环境。

启动服务：

```bash
./start.sh
docker compose ps
```

脚本会在 Docker 中启动 PostgreSQL、RabbitMQ、后端、前端及开发用 Adminer。打开 `http://localhost:3000`，用本地开发账号登录；在“服务提供商”设置中配置一个可用的 OpenAI 兼容或 Anthropic 模型及其凭据。开发环境 Adminer 地址为 `http://localhost:8082`，RabbitMQ 管理页为 `http://localhost:15672`。

查看日志、停止和重建服务：

```bash
docker compose logs -f agentx-backend
docker compose down
docker compose --profile local --profile dev up -d --build
```

## 升级已有数据库

先备份数据库，再确认当前数据库最后应用的迁移。仓库不会自动执行 `AgentX/src/main/resources/db/migration/` 中的增量 SQL；只运行尚未应用的迁移，并按文件名顺序执行。首阶段运行相关迁移及其顺序见[目标运行升级与迁移说明](run-core-upgrade.md)。不要重复执行已应用的非幂等迁移。

本地 Compose 数据库备份示例：

```bash
docker exec agentx-postgres pg_dump -U agentx_user agentx > agentx-before-upgrade.sql
```

迁移完成后，从仓库根目录重新构建并启动服务：

```bash
cd deploy
docker compose --profile local --profile dev up -d --build
docker compose ps
```

## 专用 GitHub 测试仓库

1. 在自己的 GitHub 账号或组织中创建一个专用仓库，例如 `my-account/hi-agent-acceptance`。建议设为私有仓库，并在其中放置一份可作为知识资料的项目说明。
2. 创建 fine-grained personal access token，只授权这个仓库，并只授予验收所需的 Issues 读取和写入权限。
3. 登录本地控制台，打开头像菜单中的“GitHub 连接”，提交该 PAT，并将允许仓库列表限制为这个专用仓库。
4. 在“知识库”中新建数据集并上传项目说明。等待状态显示资料已就绪并出现可检索版本。
5. 在“工作室”新建 Agent，填写职责和指令，在“工具 & 知识库”中关联刚创建的数据集。确认模型已配置并可用。
6. 打开“工作区”，选中该 Agent 并开启会话。切换到“运行”，选择授权的测试仓库和“所有外部写入都需审批”，然后提交目标，例如：

   > 根据已上传的说明，创建一个总结项目恢复流程的 Issue，并引用支持内容的资料。

7. 审阅待审批操作中的仓库、标题、正文和资料引用。批准后，确认运行时间线显示完成、资料来源可见，并且 Issue 链接指向专用测试仓库。
8. 再发起一次运行并拒绝待审批写入。确认运行记录显示拒绝，且测试仓库没有新增对应 Issue。控制台只能选择已授权仓库；仓库越权、审批边界、重启恢复和重复投递由后端回归测试覆盖。
9. 验收完成后，在“GitHub 连接”中撤销连接，并在 GitHub 撤销测试 PAT；按需删除测试 Issue 或专用仓库。

不要在验收记录、截图、日志或 Issue 正文中保存 PAT、模型 API key 或其他凭据。

## 自动化验证

浏览器流程使用 Playwright 和本地 Next.js 服务。所有 `/api` 请求均被测试替身拦截，GitHub token 是明确的虚构字符串；测试会验证没有发出外部网络请求。

```bash
cd agentx-frontend-plus
npm ci --legacy-peer-deps --include=optional
npx playwright install chromium
npm run test:e2e
```

仓库的 GitHub Actions 还会运行前端单元测试、lint、build、浏览器流程以及审批、仓库授权、运行恢复和重复投递后端测试。需要本地检查特定后端行为时，在 `AgentX` 目录运行：

```bash
./mvnw -Dtest=RunApprovalHttpTest,RunDeliveryTest,RunWorkerApprovedActionIdempotencyTest,RunVersionPinningTest,GitHubRunTargetRepositoryAuthorizationTest,GitHubConnectionApprovalAuthorizationTest,AgentToolActionApprovalPolicyTest test
```

## 验收记录

为每次人工验收记录以下信息，且不包含任何凭据：

- 代码提交 SHA、部署方式和运行时间。
- 专用测试仓库名称、运行编号和最终状态。
- 成功场景的 Issue 链接、资料文件名与引用页码；拒绝场景的运行编号和未写入结果。
- `./mvnw` 后端回归、前端检查和 `npm run test:e2e` 的通过情况。
