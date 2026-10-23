# PR 审查报告回归

`/reviews` 通过个人连接和既有只读策略固定输入，随后进入 `/reviews/reports` 选择模型并生成独立运行。报告使用公共内容产物修订：`PR_REVIEW` 的 `inputRevisionId` 固定到输入快照，参数记录 base/head。报告和精确修订下载始终检查所有者。

`SOURCE_VERIFIED` 仅表示服务端已在固定提交的已读源码中核对文件、行位与逐字引用。模型的 Bug 结论仍需人工复核。伪造文件、行位或引用保存在 `unverifiedFindings`，不生成可误认的源码链接。模型自己声称的验证状态、URL或工具请求不能覆盖服务端字段。空发现有效；报告明确未执行代码或测试，不承诺全仓无缺陷。

## HTTP 与 CI

在专用测试 PostgreSQL 中设置 `TEACHING_INTEGRATION=true`，执行 `./mvnw -Dtest=PrReviewHttpTest test`。默认 URL 为 `jdbc:postgresql://127.0.0.1:55425/teaching_test`，与 `teaching-pr-http` CI job 的临时服务一致。每例新建随机 `review_<UUID>` schema，并先断言 `current_schema()`；修改同时显式限定 schema、模型/provider id 和 owner，清理仅删除该 schema。公开 HTTP 验证持久修订、下载、所有者、真实模型注册表、失败脱敏、已知 Bug/安全对照和伪造证据。模型传输是唯一生成替身，数据库与注册表是真实实现。

## 完整应用浏览器

`src/test/java/.../PrReviewFullAppHarness` 启动真实 `AgentXApplication`，保留 Tomcat、JWT、加密个人连接、数据库、工具目录、ToolBroker、模型注册表和报告保存。仅 `ExternalGitHubBoundaries.reviewExternalIdentity` 替换外部 GitHub 身份核验，`reviewExternalMcp` 替换外部 MCP 客户端。测试配置只在 test 源，不进入生产 main 产物。18857 的独立只读测试审计端口记录无凭据的外部调用清单。

浏览器前置条件：完整应用监听 58088，专属数据库 `hi_agent_full_app_runtime` 和测试账户已初始化，GitHub 测试凭据加密 key 仅在 launcher 环境设置，正常产品中不得启用这些 fixture。模型服务 `integration/pr-review-model-fixture.py` 仅监听 18858，拒绝包含 tools/functions 的请求。目录和平台只读分类是前置测试数据；`pr-review-prepare-fixture.py <绝对输出JSON路径>` 随后通过生产 HTTP 创建 Agent、动作选择、个人连接和固定输入，绝不填入报告或绕过授权。输出 JSON 不保存 JWT。

启动上述隔离应用和外部 fixture 后：

```bash
PR_REVIEW_FIXTURE_FILE=/absolute/path/fixture.json npx playwright test --config integration/pr-review-full.config.ts
```

浏览器从输入页读取 PR 到报告页面，检查指定修订 Markdown、刷新、所有者、空发现、失败状态、手机宽度、纯文本恶意内容以及零 console/pageerror。外部审计只允许凭据核验、目录读取、PR get/get_files 与固定 SHA 文件读取。Playwright 产物放在系统临时目录，避免 Next HMR 反馈循环。

此回归证明受控外部边界下的产品链路，不代表真实 GitHub MCP 兼容性、真实模型审查质量或真实教材教师验收。真实服务验收继续保留在 #41。
