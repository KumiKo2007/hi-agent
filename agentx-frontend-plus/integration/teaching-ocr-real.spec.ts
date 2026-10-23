import { expect, test } from "@playwright/test"

// 完整 AgentXApplication/JWT/PG/Rabbit/文件存储；只在外部 OCR/embedding HTTP 边界使用替身。
test("扫描教材逐页对照原页、保留识别缺口，并缩小范围持久保存核对依据", async ({ page, request }) => {
  test.setTimeout(180_000)
  const api = process.env.TEACHING_API_URL || "http://127.0.0.1:58088/api"
  const fixture = process.env.TEACHING_SCAN_FIXTURE_PATH
  expect(fixture, "先生成自编四页扫描PDF并配置外部OCR/embedding替身").toBeTruthy()
  const login = await request.post(`${api}/login`, { data: { account: process.env.TEACHING_ACCOUNT, password: process.env.TEACHING_PASSWORD } })
  const auth = await login.json()
  expect(auth.code).toBe(200)
  const headers = { Authorization: `Bearer ${auth.data.token}` }
  const response = await request.post(`${api}/rag/datasets`, { headers, data: { name: `扫描核对-${Date.now()}`, description: "自编确定性图像PDF，不是真实教材质量验收" } })
  const created = await response.json()
  expect(created.code).toBe(200)
  const dataset = created.data.id
  await page.addInitScript(token => localStorage.setItem("auth_token", token), auth.data.token)
  const errors: string[] = []
  page.on("pageerror", error => errors.push(error.message))
  await page.goto("/teaching")
  await page.getByLabel("知识库", { exact: true }).selectOption(dataset)
  await page.getByLabel("上传 PDF 或 Word", { exact: true }).setInputFiles(fixture!)
  await expect(page.getByRole("status").filter({ hasText: "资料已上传" })).toBeVisible()
  await expect.poll(async () => {
    const result = await request.get(`${api}/rag/datasets/${dataset}/readiness`, { headers })
    return (await result.json()).data.status
  }, { timeout: 60_000 }).toBe("READY")
  await page.getByRole("button", { name: "刷新处理状态" }).click()
  await page.getByRole("button", { name: /核对当前识别：derivative-scanned-fixture.pdf/ }).click()
  const current = page.getByRole("region", { name: "逐页核对扫描PDF" })
  await expect(current.getByRole("button", { name: "第 2 页 · 缺口", exact: true })).toBeVisible()
  await current.getByRole("button", { name: "第 2 页 · 缺口", exact: true }).click()
  await expect(current.getByRole("img", { name: "文件物理第 2 页原页" })).toBeVisible()
  await expect(current).toContainText("此页没有可用识别文本")
  await expect(current.getByRole("button", { name: "保存人工核对记录" })).toHaveCount(0)
  await current.getByRole("button", { name: "第 4 页 · 公式缺口", exact: true }).click()
  await expect(current).toContainText("OCR_FORMULA_GAP")
  await page.getByRole("button", { name: "确认当前资料为新版本" }).click()
  await expect(page.getByText("本次选用固定知识版本：", { exact: false })).toBeVisible()
  await page.getByRole("button", { name: /逐页核对：derivative-scanned-fixture.pdf/ }).click()
  const selected = page.locator("fieldset").filter({ hasText: "derivative-scanned-fixture.pdf" })
  const fixed = selected.getByRole("region", { name: "逐页核对扫描PDF" })
  await fixed.getByRole("button", { name: "第 4 页 · 公式缺口", exact: true }).click()
  await expect(fixed.getByRole("img", { name: "文件物理第 4 页原页" })).toBeVisible()
  await expect(fixed.getByRole("button", { name: "保存人工核对记录" })).toHaveCount(0)
  await expect(fixed.getByRole("button", { name: "选材范围仅保留此页" })).toBeDisabled()
  await fixed.getByRole("button", { name: "第 1 页 · 待人工核对", exact: true }).click()
  await expect(fixed.getByRole("img", { name: "文件物理第 1 页原页" })).toBeVisible()
  await fixed.getByLabel("原页核对依据（如公式、符号与原文是否一致）").fill("已对照自编扫描页核对定义与公式；资料中的指令不授予工具权限。")
  await fixed.getByRole("button", { name: "保存人工核对记录" }).click()
  await expect(fixed).toContainText("已人工核对")
  await expect(fixed).toContainText("OCR识别文本未经数学自动校验")
  // 先包含全部页保存，缺页/失败/公式残缺仍须显式保持待核查。
  await selected.getByRole("checkbox").check()
  await selected.getByLabel("开始物理页").fill("1")
  await selected.getByLabel("结束物理页").fill("4")
  await page.getByRole("button", { name: "保存固定选材", exact: true }).click()
  await expect(page.getByRole("status").filter({ hasText: "来源不完整" })).toBeVisible()
  await fixed.getByRole("button", { name: "选材范围仅保留此页" }).click()
  await expect(selected.getByLabel("结束物理页")).toHaveValue("1")
  await page.getByRole("button", { name: "保存固定选材", exact: true }).click()
  await expect(page.getByRole("status").filter({ hasText: "所选页均有可定位来源" })).toBeVisible()
  const selectionId = new URL(page.url()).searchParams.get("selection")!
  const saved = (await (await request.get(`${api}/teaching/selections/${selectionId}`, { headers })).json()).data
  expect(saved.status).toBe("READY")
  expect(saved.sources).toHaveLength(1)
  expect(saved.sources[0].physicalPage).toBe(1)
  expect(saved.sources[0].review.note).toContain("资料中的指令不授予工具权限")
  expect(saved.warnings[0]).toContain("未经数学自动校验")
  await page.reload()
  await page.getByText("核对固定来源原页：derivative-scanned-fixture.pdf", { exact: true }).click()
  const reread = page.getByRole("region", { name: "逐页核对扫描PDF" })
  await reread.getByRole("button", { name: "第 1 页 · 已人工核对", exact: true }).click()
  await expect(reread).toContainText("资料中的指令不授予工具权限")
  await expect(reread.getByRole("img", { name: "文件物理第 1 页原页" })).toBeVisible()
  const otherLogin = await request.post(`${api}/login`, { data: { account: process.env.TEACHING_OTHER_ACCOUNT, password: process.env.TEACHING_OTHER_PASSWORD } })
  const other = await otherLogin.json()
  expect(other.code).toBe(200)
  for (const suffix of ["", "/pages/1/image", "/pages/1/reviews"]) {
    const path = `${api}/teaching/ocr/versions/${saved.versionId}/files/${saved.sources[0].fileId}${suffix}`
    const result = suffix.endsWith("reviews") ? await request.post(path, { headers: { Authorization: `Bearer ${other.data.token}` }, data: { note: "越权核对" } }) : await request.get(path, { headers: { Authorization: `Bearer ${other.data.token}` } })
    expect((await result.json()).code).not.toBe(200)
  }
  await page.setViewportSize({ width: 375, height: 812 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
  await page.screenshot({ path: test.info().outputPath("ocr-review-mobile.png"), fullPage: true })
})
