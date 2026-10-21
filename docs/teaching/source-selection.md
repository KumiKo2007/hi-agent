# 教学选材

教师从“教学准备”选择自己的知识库，沿用现有上传/处理流程。全部文件处理完成后可以确认新的可检索知识版本；旧版本不自动纳入后来上传的资料。

保存前必须明确选择版本中的文件、从 1 开始的文件物理页范围以及可选章节标记。章节名仅作标记，实际采用的内容由文件和页范围决定。PDF 的文件物理页不等于课本印刷页。非 PDF 的现有处理单元不会被标成文件物理页，只能保存为待核查选材；Word 段落定位由后续任务交付。

`POST /teaching/selections` 创建不可变选材，后续重新保存产生新 ID。保存包含知识版本、明确范围和实际选中的来源内容；读取及来源打开均按当前 UserContext 的所有者过滤。来源快照不会跟随知识库更新，且不会包含未选文件或范围外页。缺页或空内容会产生 `INCOMPLETE` 和行动提示，后续生成不得默认其来源充分。

`GET /teaching/selections` 列出当前所有者选材，`GET /teaching/selections/{id}` 重读固定内容，`GET /teaching/selections/{id}/sources/{sourceId}` 打开该选材内的一条来源。来源 `locatorType=FILE_PAGE` 时 `physicalPage` 是 PDF 文件物理页；`DOCUMENT_UNIT` 只表示历史处理单元序号，不可解释为物理页或 Word 段落。

## 验证与限制

`TeachingSelectionHttpTest` 在独立 PostgreSQL schema 上通过公开 HTTP 验证范围隔离、固定内容、来源重读及越权拒绝。运行需 `TEACHING_INTEGRATION=true`；默认连接本机 55425 的独立 `teaching_test` 数据库，密码只用于隔离测试容器。可用 `TEACHING_TEST_URL` 指定专用测试数据库；每次测试创建并删除随机 schema，不删除已有 schema。

`TeachingSelectionBrowserHarness` 将实际选材 controller/app/store 暴露在本机 8088/api，注入专用测试身份，不替换生产知识库、上传或身份认证实现。它只用于读取/刷新/来源权限的浏览器 slice；这些范围以外的路由返回失败。

`integration/teaching-read.spec.ts` 连接上述真实选材 HTTP，不拦截网络响应。完整应用的 `integration/teaching-real.spec.ts` 另需真实测试账户 token 以及预置的知识版本参数，验证页面选材、保存、刷新与越权。上传、处理/readiness、真实 OCR/课本及教师复核仍需在完整应用集成验收中完成，浏览器 slice 不替代这些验收。
