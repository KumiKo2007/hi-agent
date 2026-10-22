import { expect, test } from "@playwright/test"

// 实际选材 controller/app/store + PostgreSQL；不替换 HTTP 响应。
// 此 slice 不覆盖知识库选择/上传/readiness/JWT，完整路径由 teaching-real.spec.ts 单独验证。
test("从实际 HTTP 保存的选材可在浏览器刷新、打开来源并拒绝越权", async ({ page, request, browser }) => {
  const ownerToken = process.env.TEACHING_OWNER_TOKEN!
  const otherToken = process.env.TEACHING_OTHER_TOKEN!
  expect(ownerToken, "先启动 TeachingSelectionBrowserHarness").toBeTruthy()
  const response = await request.post("http://127.0.0.1:8088/api/teaching/selections", {
    headers: { Authorization: `Bearer ${ownerToken}` },
    data: { title: "导数几何意义", datasetId: "dataset", versionId: "v1", ranges: [{ fileId: "f1", startPage: 2, endPage: 2, chapter: "第一课" }] },
  })
  expect(response.ok()).toBeTruthy()
  const selection = (await response.json()).data
  expect(selection.sources).toHaveLength(1)
  expect(selection.sources[0].physicalPage).toBe(2)
  await page.addInitScript(token => localStorage.setItem("auth_token", token), ownerToken)
  await page.goto(`/teaching?selection=${selection.id}`)
  await expect(page.getByText("固定来源：导数几何意义", { exact: true })).toBeVisible()
  await page.getByRole("button", { name: "导数.pdf · 文件物理第 2 页", exact: true }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toHaveText(/切线斜率/)
  await page.reload()
  await expect(page.getByText("固定来源：导数几何意义", { exact: true })).toBeVisible()
  await page.getByRole("button", { name: "导数.pdf · 文件物理第 2 页", exact: true }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toHaveText(/切线斜率/)
  const other = await browser.newContext()
  await other.addInitScript(token => localStorage.setItem("auth_token", token), otherToken)
  const intruder = await other.newPage()
  await intruder.goto(`/teaching?selection=${selection.id}`)
  await expect(intruder.getByRole("alert").filter({ hasText: "选材不存在或无访问权限" })).toBeVisible()
  await expect(intruder.getByRole("region", { name: "来源原文" })).toHaveCount(0)
  await other.close()
})


test("Word来源在实际HTTP保存后显示原文段落与标题，并保留覆盖警告", async ({ page, request }) => {
  const token = process.env.TEACHING_OWNER_TOKEN!
  const response = await request.post("http://127.0.0.1:8088/api/teaching/selections", {
    headers: { Authorization: `Bearer ${token}` },
    data: { title: "Word导数备课", datasetId: "dataset", versionId: "v1", ranges: [{ fileId: "word-f1", startPage: 3, endPage: 3, chapter: "导数" }] },
  })
  expect(response.ok()).toBeTruthy()
  const saved = (await response.json()).data
  expect(saved.status).toBe("INCOMPLETE")
  expect(saved.sources[0].physicalPage).toBeNull()
  await page.addInitScript(value => localStorage.setItem("auth_token", value), token)
  await page.goto(`/teaching?selection=${saved.id}`)
  await expect(page.getByText("固定来源：Word导数备课", { exact: true })).toBeVisible()
  await page.getByRole("button", { name: "导数备课.docx · 原文第 3 段 · 导数的概念", exact: true }).click()
  const source = page.getByRole("region", { name: "来源原文" })
  await expect(source).toContainText("割线的斜率趋近于切线的斜率")
  await expect(source).toContainText("公式结构未覆盖")
  await page.reload()
  await page.getByRole("button", { name: "导数备课.docx · 原文第 3 段 · 导数的概念", exact: true }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText("公式结构未覆盖")
})
