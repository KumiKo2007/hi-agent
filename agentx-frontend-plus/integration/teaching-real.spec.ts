import { expect, test } from "@playwright/test"

// 本测试连接实际应用 HTTP，不拦截页面请求。预置独立所有者与就绪教材版本；环境缺失明确失败。
test("教师保存限定页范围，刷新后重读固定来源并拒绝越权", async ({ page, request, browser }) => {
  const required = ["TEACHING_OWNER_TOKEN", "TEACHING_OTHER_TOKEN", "TEACHING_DATASET_ID", "TEACHING_VERSION_ID", "TEACHING_FILE_ID"] as const
  for (const name of required) expect(process.env[name], `缺少真实测试环境参数 ${name}`).toBeTruthy()
  const token = process.env.TEACHING_OWNER_TOKEN!
  const api = process.env.TEACHING_API_URL || "http://127.0.0.1:8088/api"
  const headers = { Authorization: `Bearer ${token}` }
  const title = `浏览器选材验收 ${Date.now()}`
  await page.addInitScript(value => localStorage.setItem("auth_token", value), token)
  await page.goto("/teaching")
  await page.getByLabel("知识库", { exact: true }).selectOption(process.env.TEACHING_DATASET_ID!)
  await expect(page.getByText(/本次选用固定知识版本/)).toBeVisible()
  const checkbox = page.getByRole("checkbox", { name: process.env.TEACHING_FILE_NAME || "导数.pdf", exact: true })
  await checkbox.check()
  await page.getByLabel("选材名称", { exact: true }).fill(title)
  await page.getByLabel("开始物理页", { exact: true }).fill("2")
  await page.getByLabel("结束物理页", { exact: true }).fill("2")
  await page.getByLabel("章节标记", { exact: true }).fill("导数几何意义")
  const save = page.waitForResponse(response => response.url().endsWith("/teaching/selections") && response.request().method() === "POST")
  await page.getByRole("button", { name: "保存固定选材", exact: true }).click()
  const response = await save
  expect(response.ok()).toBeTruthy()
  const selected = (await response.json()).data
  expect(selected.versionId).toBe(process.env.TEACHING_VERSION_ID)
  expect(selected.sources.length).toBeGreaterThan(0)
  expect(selected.sources.every((source: { fileId: string; physicalPage: number }) => source.fileId === process.env.TEACHING_FILE_ID && source.physicalPage === 2)).toBeTruthy()
  await page.reload()
  await expect(page.getByText(`固定来源：${title}`, { exact: true })).toBeVisible()
  await page.getByRole("button", { name: /文件物理第 2 页/ }).first().click()
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText(selected.sources[0].content)
  const reread = await request.get(`${api}/teaching/selections/${selected.id}`, { headers })
  expect((await reread.json()).data.sources).toEqual(selected.sources)
  const intruder = await browser.newContext()
  await intruder.addInitScript(value => localStorage.setItem("auth_token", value), process.env.TEACHING_OTHER_TOKEN!)
  const other = await intruder.newPage()
  await other.goto(`/teaching?selection=${selected.id}`)
  await expect(other.getByRole("alert")).toBeVisible()
  await expect(other.getByRole("region", { name: "来源原文" })).toHaveCount(0)
  const denied = await request.get(`${api}/teaching/selections/${selected.id}/sources/${selected.sources[0].id}`, {
    headers: { Authorization: `Bearer ${process.env.TEACHING_OTHER_TOKEN}` },
  })
  expect(denied.status()).toBe(400)
  await intruder.close()
})
