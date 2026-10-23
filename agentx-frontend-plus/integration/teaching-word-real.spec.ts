import { expect, test } from "@playwright/test"

// 真实 AgentXApplication/JWT/PG/Rabbit 的公开 HTTP 已产生固定选材；这里不拦截或替换产品请求。
test("实际应用中的 Word 固定来源在页面重读原文位置与未覆盖警告", async ({ page, request }) => {
  const api = process.env.TEACHING_API_URL || "http://127.0.0.1:58088/api"
  const plainId = process.env.TEACHING_WORD_PLAIN_SELECTION_ID
  const complexId = process.env.TEACHING_WORD_COMPLEX_SELECTION_ID
  expect(plainId, "先通过完整应用上传并保存纯文本 Word 选材").toBeTruthy()
  expect(complexId, "先通过完整应用上传并保存含公式的 Word 选材").toBeTruthy()
  const response = await request.post(`${api}/login`, { data: { account: process.env.TEACHING_ACCOUNT, password: process.env.TEACHING_PASSWORD } })
  expect(response.ok()).toBeTruthy()
  const login = await response.json()
  expect(login.code).toBe(200)
  await page.addInitScript(token => localStorage.setItem("auth_token", token), login.data.token)
  const failures: string[] = []
  page.on("pageerror", error => failures.push(error.message))
  page.on("console", message => {
    if (message.type() === "error") failures.push(message.text())
  })
  await page.goto(`/teaching?selection=${plainId}`)
  await expect(page.getByRole("button", { name: /word-source-plain.docx · 原文第 3 段 · 导数的概念/ })).toBeVisible()
  await page.getByRole("button", { name: /word-source-plain.docx · 原文第 3 段 · 导数的概念/ }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText("割线的斜率趋近于切线的斜率。")
  await expect(page.locator("main [role=alert]")).toHaveCount(0)
  await page.reload()
  await page.getByRole("button", { name: /word-source-plain.docx · 原文第 3 段 · 导数的概念/ }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText("割线的斜率趋近于切线的斜率。")
  await page.goto(`/teaching?selection=${complexId}`)
  await page.getByRole("button", { name: /word-source-complex.docx · 原文第 3 段 · 导数的概念/ }).click()
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText("公式结构未覆盖")
  await expect(page.getByRole("region", { name: "来源原文" })).toContainText("表格内容未覆盖")
  await expect(page.locator("main [role=alert]")).toHaveCount(0)
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(failures).toEqual([])
})
