import { expect, test } from "@playwright/test"

// 浏览器直连实际HTTP controller/app/MyBatis/JDBC；仅外部GitHub MCP受控。
// 测试身份通过fixture UserContext seam注入，不覆盖生产JWT或完整应用启动。
function token(user: string) {
  return `e30.${Buffer.from(JSON.stringify({ userId: user, exp: 2540188800 })).toString("base64url")}.pr-browser-test`
}

test("授权PR固定输入、刷新恢复、越权和非法目标拒绝", async ({ page, request }) => {
  const api = process.env.PR_INPUT_API_URL || "http://127.0.0.1:18837/api"
  await page.addInitScript(value => localStorage.setItem("auth_token", value), token("owner"))
  await page.goto("/reviews")
  await expect(page.getByLabel("个人 GitHub 连接")).toContainText("alice")
  await expect(page.getByLabel("读取能力")).toContainText("代码助理")
  await page.getByLabel("GitHub PR 链接").fill("https://github.com/alice/repo/pull/42")
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByText("b".repeat(40), { exact: true })).toBeVisible()
  await expect(page.getByText("a".repeat(40), { exact: true })).toBeVisible()
  const id = new URL(page.url()).searchParams.get("input")
  expect(id).toBeTruthy()
  await page.reload()
  await expect(page.getByText("b".repeat(40), { exact: true })).toBeVisible()
  await page.getByText("src/math.ts · modified", { exact: true }).click()
  await expect(page.getByText(/return 1/).first()).toBeVisible()
  const fixed = await request.get(`${api}/reviews/inputs/${id}`, { headers: { Authorization: `Bearer ${token("owner")}` } })
  expect(fixed.status()).toBe(200)
  const snapshot = (await fixed.json()).data
  expect(snapshot.contexts.some((entry: { path: string }) => entry.path === "tests/math.test.ts")).toBe(true)
  expect(snapshot.gaps.join(" ")).toContain("未运行测试")
  expect(JSON.stringify(snapshot)).not.toContain("github_pat_test_only_token")
  const denied = await request.get(`${api}/reviews/inputs/${id}`, { headers: { Authorization: `Bearer ${token("other")}` } })
  expect(denied.status()).toBe(400)
  await page.getByLabel("GitHub PR 链接").fill("https://github.com/bob/private/pull/1")
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByRole("alert", { name: "PR 输入错误" })).toContainText("授权范围")
  await page.getByLabel("GitHub PR 链接").fill("https://github.com/alice/repo/issues/42")
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByRole("alert", { name: "PR 输入错误" })).toContainText("请输入有效")
})
