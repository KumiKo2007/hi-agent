# Word 教学资料来源

教学准备页面可上传 PDF、DOCX 或 DOC，并使用既有上传、文件处理及知识版本流程。Word 正文按照原文段落保存，从 1 开始计数，空段落也占编号；同一长段不会再被 500 字切段拆成多个虚构位置。Word 不提供可靠的排版页码。

固定知识版本及选材来源保存 `sourceLocatorType`、`sourceLocator` 与 `coverageWarnings`。新 DOCX 的定位类型为 `WORD_PARAGRAPH`，定位 JSON 包含 `paragraphIndex` 与最近的标题 `heading`；教学来源响应中 `physicalPage` 为 null。选材请求的兼容字段 `startPage/endPage` 在 Word 下表示正文段落范围，页面明确标注该含义。来源打开与重读始终校验选材所有者与所选范围。

DOCX 当前可靠导入正文段落文本。检测到公式、嵌图、绘图、嵌入对象、表格、内容控件、修订及页眉脚注等未完整覆盖内容时，警告随原文、固定知识快照和固定选材保留，选材为 `INCOMPLETE`。旧 DOC 仅保证正文段落文本，复杂格式一律提示核查。Word 损坏、加密、没有可用正文时处理失败；不会生成空的成功来源。

历史 Word 的切段无法自动还原真实原文段落。空定位保留为 `DOCUMENT_UNIT`，提示重新处理并固定新版本；不会修改已保存选材或用新资料冒充旧版本来源。

升级既有实例需执行 `V20261020002__word_source_locations.sql`。全新安装已同步 `docs/sql/01_init.sql`；部署未自动启用 Flyway，不应仅复制迁移文件就声称既有数据库已升级。

验证分层：`TeachingSelectionHttpTest` 检查固定位置、范围、所有者与重新读取；`WordSourceHttpTest` 使用实际 DOCX、生产文件存储/记录器、POI 和 PostgreSQL 后通过选材公开 HTTP 观察结果；浏览器 slice 使用真实选材 HTTP/PG 检查段落、标题、警告、刷新及拒绝越权。slice 不验证上传 MQ、JWT 或真实模型，完整应用验收另记。

Apache POI 的正文/表格/页眉接口及限制依据：[XWPF 官方指南](https://poi.apache.org/components/document/quick-guide-xwpf.html)。POI 5.4.0 的 ZIP/IO 依赖按照其已发布 POM 固定为 commons-compress 1.27.1、commons-io 2.18.0，避免其他解析依赖选择旧 API 导致 DOCX 读写失败。

真实应用上传回归还发现并修复了两个装配问题：RAG 消费者自行确认消息，因此自建 Rabbit 工厂使用 `MANUAL`，而声明 `AUTO` 的 Run listener 保持容器确认；新文件及待处理状态先提交数据库，再发布初始处理消息，避免消费者在上传事务提交前报告“文件不存在”。提交后消息发送失败使用独立事务保存启动失败状态。

2026-10-22 的本机验收使用真实 `AgentXApplication`、签名 JWT、PostgreSQL、RabbitMQ、本地文件存储、DOCX 解析及知识版本。纯文本、含公式表格、损坏 DOCX 分别验证可用来源、显式未覆盖、处理失败；公开 HTTP 拒绝其他所有者、无 JWT 及未选中来源。独立 Chrome 验证 Word 第 3 段及标题、来源重读/刷新、未覆盖警告和 375px 宽度；同时断言没有浏览器异常或控制台错误。外部 embedding 返回固定向量，仅用于处理管线验证，不证明检索/教学质量；用户教材、真实模型与教师审核仍属于 #36 的待验收范围。
