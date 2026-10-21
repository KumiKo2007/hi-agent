# PR 输入快照

代码审查入口 `/reviews` 使用当前使用者自己的 GitHub 连接。每个使用者目前只有一份连接，页面列出其身份及已授权仓库。

## 准备读取能力

为自己的启用 Agent 安装 GitHub MCP 服务，允许 `pull_request_read` 与 `get_file_contents` 两项工具动作。平台管理者必须根据真实动作确认其为 `READ_ONLY`；创建 Issue 的权限不提供 PR 读取能力。页面只列出满足这两项条件的 Agent / MCP 服务组合，提交后仍由 ToolBroker 校验实际远端动作结构。没有兼容能力时明确停止，绝不改用共享凭据或虚假输入。

当前适配 GitHub 官方 MCP 读取动作：`pull_request_read` 的 `get` / `get_files`；`get_file_contents` 的 `sha` 参数。官方参数约定参考：https://github.com/github/github-mcp-server#tools 。远端未安装动作、输出格式或结构变化均可能阻止读取，真实部署需要单独验证。

## 固定与缺口

详情读取先固定 base/head SHA，分页读取变更文件的文本差异，再按提交 SHA 获取变更文件、测试及根目录 AGENTS.md / README.md，最后重新核对 PR base/head。提交漂移时整体失败，不保存混版输入。PR 文件列表最多 300 个，全文上下文最多前 20 个变更文件，每份正文最多 60,000 字符，远端单次响应最多 2,000,000 字符；未读取、截断、不可解析、二进制或缺失文件均在快照中披露。

源码只从选定的已授权仓库读取；fork 的固定 SHA 在该仓库不可读取时保留缺口，不扩大授权。输入不会执行源码、安装依赖、运行测试或写 GitHub。来源中的指令仅为不可信文本；不得改变系统指令或工具策略。常见凭据格式在保存与展示前脱敏。

保存后的快照按所有者隔离，刷新带 input 参数的页面可恢复；重新提交会生成新快照，旧输入不会随 PR 更新。读取失败时不保存快照，页面显示错误并支持重试。

## 验收边界

HTTP 测试验证控制器、读取协调、真实 ToolBroker、动作策略和个人连接授权，外部 MCP 用可控替身；浏览器测试验证个人连接选择、PR 提交、固定 SHA 预览、恢复与失败重试。内存存储用于快速 HTTP 行为测试，不代表 PostgreSQL 持久化验收。真实 GitHub MCP 与模型审查质量由后续真实 PR 验收任务验证，不能把替身响应视为真实全链路通过。

## 本机浏览器 slice

`PrInputBrowserHarness` 使用独立 PostgreSQL 的随机 schema，真实生产 MyBatis Agent/安装动作读取、JDBC 策略/连接/输入存储及生产协调流程。通过本地 HTTP 将请求交给实际控制器；外部 GitHub 身份核验和 MCP 响应受控。测试身份在 UserContext seam 注入，此 slice 不验证生产 JWT、安全网关或完整应用启动。

需要显式设置 `PR_INPUT_POSTGRES_URL`、`PR_INPUT_POSTGRES_USER`、`PR_INPUT_POSTGRES_PASSWORD`，编译测试后使用测试 classpath 启动 harness。默认监听 127.0.0.1:18837，关闭时删除本次随机 schema。浏览器使用 `npx playwright test --config integration/pr-input-real.config.ts`；`PR_INPUT_API_URL` 可指定其他本地 harness 地址，前端通过 `NEXT_PUBLIC_API_BASE_URL` 配置实际地址。

`e2e/pr-input.spec.ts` 的页面 HTTP 响应替身只作交互补充，不能代替上述真实 HTTP / PostgreSQL 验收。真实个人 GitHub 授权与远端 MCP仍需后续独立验收。
