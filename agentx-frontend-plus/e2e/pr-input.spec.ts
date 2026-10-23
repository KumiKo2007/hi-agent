import { expect, test } from "@playwright/test"

const baseSha = "a".repeat(40)
const headSha = "b".repeat(40)
const snapshot = {
  id: "input-1", agentId: "agent", serviceId: "github", repository: "alice/repo", pullNumber: 42,
  pullRequestUrl: "https://github.com/alice/repo/pull/42", baseSha, headSha,
  title: "修复求导", body: "只读输入", createdAt: "2026-10-20T00:00:00Z",
  files: [{ path: "math.ts", previousPath: null, status: "modified", patch: "@@ -1 +1 @@\n-return 1\n+return 2" }],
  contexts: [{ repository: "alice/repo", path: "math.ts", commitSha: headSha, content: "return 2" }],
  gaps: ["未运行测试；未读取完整仓库。"],
}

test("个人连接提交 PR 并恢复固定输入，读取失败可重试", async ({ page }) => {
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ userId: "1", exp: 2540188800 }))
    localStorage.setItem("auth_token", `e30.${payload}.signature`)
  })
  let fail = false
  let submissions = 0
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url())
    let data: unknown = null
    if (url.pathname.endsWith("/reviews/input-options")) {
      data = { connection: { githubLogin: "alice", githubUserId: "1", repositories: ["alice/repo"] }, choices: [{ agentId: "agent", agentName: "代码助理", serviceId: "github" }] }
    } else if (url.pathname.endsWith("/reviews/inputs") && route.request().method() === "POST") {
      submissions++
      expect(route.request().postDataJSON()).toEqual({ agentId: "agent", serviceId: "github", pullRequestUrl: snapshot.pullRequestUrl })
      if (fail) return route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ code: 400, message: "目标仓库不在 GitHub 连接授权范围内" }) })
      data = snapshot
    } else if (url.pathname.endsWith("/reviews/inputs/input-1")) data = snapshot
    else if (url.pathname.endsWith("/users/info")) data = { id: "1", nickname: "教师" }
    await route.fulfill({ contentType: "application/json", body: JSON.stringify({ code: 200, message: "成功", data }) })
  })
  await page.goto("/reviews")
  await expect(page.getByLabel("个人 GitHub 连接")).toContainText("alice")
  await page.getByLabel("GitHub PR 链接").fill(snapshot.pullRequestUrl)
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByText(headSha, { exact: true })).toBeVisible()
  await expect(page.getByText(baseSha, { exact: true })).toBeVisible()
  await expect(page.getByText("未运行测试；未读取完整仓库。", { exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByText(headSha, { exact: true })).toBeVisible()
  expect(submissions).toBe(1)
  fail = true
  await page.getByLabel("GitHub PR 链接").fill(snapshot.pullRequestUrl)
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByRole("alert", { name: "PR 输入错误" })).toContainText("目标仓库不在 GitHub 连接授权范围内")
  fail = false
  await page.getByRole("button", { name: "读取并固定输入" }).click()
  await expect(page.getByText(headSha, { exact: true })).toBeVisible()
})
