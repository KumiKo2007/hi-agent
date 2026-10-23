# 教学模块大纲

先在 `/teaching` 保存来源齐备的固定选材，再进入“设置教学参数与模块大纲”。选材状态为 `INCOMPLETE` 时先处理缺页/定位问题。来源只是模型输入数据，模型没有工具动作权限。

填写学科、年级、教材名称、具体版本/版次、模块及总课时，默认每课时 45 分钟、学生基础中等。教材版本未核对时必须明确填写“版本待核对”，确认教学范围不代表系统验证了某一版教材。总课时是教师明确确认的安排，不由系统猜测。

选择本人可用的已启用对话模型。沿用已有模型/服务商设置，检查模型和服务商所有者、启用状态与凭据。目标创建时固定模型 ID 和连接协议，执行时再次校验；协议变化、权限撤销、服务商停用均不能继续生成。

大纲生成是独立的内容目标/Run。页面先持久化目标，再调用模型；模型失败仍可刷新读取状态和安全错误。生成结果必须具有精确的课时数、每课主题和非空学习目标。未配置模型时明确报错，不返回示例大纲。

生成后可修改主题和学习目标，保存产生新修订并清除旧确认。只有精确确认当前修订后，`approved` 接口才允许后续逐课材料将它作为依据。旧修订和原始模型产物保留，不随编辑或知识库变化而覆盖。

## 公开 HTTP

所有接口沿用当前用户身份和 `Result<T>` 响应。

- `POST /teaching/outlines`：`{selectionId, modelId, subject, level, textbook, edition, module, lessonMinutes?, studentFoundation?, totalLessons, scopeConfirmed:true}`。
- `GET /teaching/outlines`：当前所有者最多 100 条大纲。
- `GET /teaching/outlines/{id}`：参数、固定版本、当前修订、确认关系和独立 Run 状态。
- `POST /teaching/outlines/{id}/generate`：原子占用 `PENDING` Run。已成功的重复调用返回同一产物，运行中或失败的调用不能重复占用。
- `PUT /teaching/outlines/{id}`：`{expectedRevisionId, lessons:[{title, objectives:[]}]}`；过期修订拒绝保存，不覆盖新编辑。
- `POST /teaching/outlines/{id}/confirm`：`{expectedRevisionId}`；只能确认当前可见修订。
- `GET /teaching/outlines/{id}/approved`：只有当前修订已确认时成功。
- `GET /teaching/outlines/{id}/revisions/{revisionId}`：读取属于此产物及当前所有者的不可变历史修订。

## 部署与扩展

升级已有实例须执行 `V20261020004__generation_artifacts.sql`。仓库尚未启用自动 Flyway 执行，不能仅复制 migration 文件就认为数据库升级完成。全新安装 SQL 已同步这三个表。

`generation_runs`、`generation_artifacts` 与 `artifact_revisions` 独立于旧 `agent_runs`，旧运行不需要回填。新修订保留所有者、Run、父修订、输入修订、知识版本和内容。后续双稿/PR 审查可复用该关系；逐课材料必须固定已确认大纲的修订 ID 到 `inputRevisionId`，不能只存一个会移动的当前指针。

本阶段为同步文本生成，持久 `RUNNING` 在进程崩溃后可能需要恢复；自动恢复、失败续跑、取消与导出版本一致性在 #35 处理。不能把当前的重复调用防护宣称为完整恢复验收。

## 验证范围

`TeachingOutlineHttpTest` 经公开 HTTP 到真实隔离 PostgreSQL，使用真实模型/服务商注册表与权限校验，只替换外部模型传输响应。`TeachingOutlineBrowserHarness` 与 `playwright.outline.config.ts` 验证页面参数、修改、确认、刷新、宽度和越权。浏览器 harness 在 UserContext 注入专用测试身份，未验证生产 JWT；没有替换 HTTP 产物响应。

真实教材路径、具体 A/B 版本、模型服务与教师复核尚未齐备，确定性样例不代表真实教学质量验收。

最终加强的浏览器检查仍保留失败：专用 harness 未装配 `/accounts/current`，外层账户上下文会收到 500；没有忽略该错误或移除控制台断言。此前大纲核心行为已单独通过，但完整页面/账户/JWT 链须由 #36 在完整应用中补齐，不能把该 slice 宣称为全应用通过。
