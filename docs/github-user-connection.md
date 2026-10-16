# 使用者 GitHub 连接

在 Web 控制台的「个人设置 → GitHub 连接」中提交自己的 fine-grained personal access token 和允许写入的仓库。创建令牌时在 GitHub 选择仓库并授予 **Issues: Read and write**。令牌的 GitHub 端仓库范围和平台保存的仓库名单共同限制可用范围；在本平台缩小名单之后，要增加仓库须重新提交并核验令牌。连接或收紧范围不创建 Issue。

后端需要 `HI_AGENT_GITHUB_TOKEN_KEY` 环境变量：用 `openssl rand -base64 32` 生成 32 字节随机密钥。未配置或格式错误时，连接操作和凭据使用都会拒绝；不要把密钥写入仓库。服务重启和升级必须保留原密钥，否则已保存的连接无法解密，使用者需要重新连接。密钥轮换需要先让使用者撤销并重建连接。

全新部署的 `docs/sql/01_init.sql` 包含 `github_connections` 表。现有数据库需执行 `AgentX/src/main/resources/db/migration/V20261015011__create_github_connections.sql`；当前工程不会自动执行此目录中的 SQL。

公开接口均需要当前使用者的登录 JWT：

- `GET /api/users/github-connection`：读取身份和仓库名单，未连接时 `data` 为 `null`。
- `POST /api/users/github-connection`：提交 `{ "token": "…", "repositories": ["owner/repo"] }`，服务端向 GitHub 核验身份及可访问仓库，并存储加密后的令牌。
- `PUT /api/users/github-connection/repositories`：提交已有名单的非空子集，只能缩小范围。
- `DELETE /api/users/github-connection`：删除本平台的连接，后续工具取用凭据会被拒绝。

响应只包含 GitHub 身份和仓库名单，不返回令牌或密文。令牌只由受信任的服务端连接适配层通过 `GitHubConnectionAppService.withAuthorizedToken` 使用；实际外部写入前须重新读取连接并检查仓库。GitHub 令牌若缺少 Issues 写权限，GitHub 会在真正写入时拒绝；只读核验不能证明这项写权限。平台撤销只删除本地连接，使用者如需在 GitHub 端停用令牌，需在 GitHub 令牌设置中再撤销。

参考 GitHub 官方文档：[fine-grained token 的 Issues 权限](https://docs.github.com/en/rest/issues/issues#create-an-issue)、[列出当前用户可访问的仓库](https://docs.github.com/en/rest/repos/repos#list-repositories-for-the-authenticated-user)。
