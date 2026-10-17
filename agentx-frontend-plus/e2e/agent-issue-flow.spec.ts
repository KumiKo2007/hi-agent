import { expect, test, type Page, type Route } from "@playwright/test"

const ownerId = "e2e-owner"
const datasetId = "dataset-e2e"
const agentId = "agent-e2e"
const sessionId = "session-e2e"
const repository = "e2e-owner/hi-agent-test"
const issueUrl = `https://github.com/${repository}/issues/42`

const now = "2026-10-17T00:00:00Z"

function apiResponse<T>(data: T) {
  return { code: 200, message: "ok", data, timestamp: Date.parse(now) }
}

function emptyPage<T>() {
  return { records: [] as T[], total: 0, size: 15, current: 1, pages: 0 }
}

function eventDetails(overrides: Record<string, unknown> = {}) {
  return {
    failureCode: null,
    modelName: null,
    inputTokens: null,
    outputTokens: null,
    usageStatus: null,
    durationMs: null,
    knowledgeVersionId: "knowledge-version-e2e",
    sourceDocumentId: null,
    sourceFileId: null,
    sourceName: null,
    sourcePage: null,
    sourceUrl: null,
    serviceId: null,
    actionName: null,
    targetRepository: null,
    actorId: null,
    decision: null,
    resultReference: null,
    ...overrides,
  }
}

function makeRun(id: string, goal: string, status: "WAITING_APPROVAL" | "COMPLETED" | "FAILED", decision?: string) {
  const completed = status === "COMPLETED"
  const denied = status === "FAILED"
  const timeline = [
    {
      id: `${id}-created`, runId: id, stepId: null, eventType: "RUN_CREATED", status: "SUCCEEDED",
      summary: "已固定 Agent、工具目录和知识版本。", details: eventDetails(), createdAt: now,
    },
    {
      id: `${id}-source`, runId: id, stepId: null, eventType: "KNOWLEDGE_SOURCE", status: "SUCCEEDED",
      summary: "引用了上传的项目资料。",
      details: eventDetails({ sourceFileId: "file-e2e", sourceName: "project-notes.txt", sourcePage: 1 }),
      createdAt: now,
    },
    {
      id: `${id}-approval-requested`, runId: id, stepId: "step-e2e", eventType: "APPROVAL_REQUESTED",
      status: "WAITING", summary: "等待所有者审批 GitHub Issue 创建。",
      details: eventDetails({ serviceId: "github", actionName: "create_issue", targetRepository: repository }),
      createdAt: now,
    },
  ]
  if (completed || denied) {
    timeline.push({
      id: `${id}-decision`, runId: id, stepId: "step-e2e", eventType: "APPROVAL_DECISION",
      status: decision === "APPROVE" ? "SUCCEEDED" : "FAILED",
      summary: decision === "APPROVE" ? "所有者批准并完成写入。" : "所有者拒绝了外部写入。",
      details: eventDetails({
        serviceId: "github", actionName: "create_issue", targetRepository: repository,
        actorId: ownerId, decision, resultReference: completed ? issueUrl : null,
      }),
      createdAt: now,
    })
  }
  if (completed) {
    timeline.push({
      id: `${id}-completed`, runId: id, stepId: "step-e2e", eventType: "RUN_COMPLETED",
      status: "SUCCEEDED", summary: "GitHub Issue 已创建。", details: eventDetails(), createdAt: now,
    })
  }

  return {
    id,
    sessionId,
    agentId,
    knowledgeVersionId: "knowledge-version-e2e",
    targetRepository: repository,
    goal,
    status,
    result: completed ? "已创建并记录 GitHub Issue。" : denied ? "所有者拒绝了写入。" : null,
    errorMessage: null,
    createdAt: now,
    updatedAt: now,
    approvalPolicy: "REQUIRE_APPROVAL_FOR_ALL_WRITES" as const,
    steps: [{
      id: "step-e2e", runId: id, sequenceNo: 1, kind: "TOOL_ACTION",
      status: completed ? "SUCCEEDED" : denied ? "FAILED" : "WAITING_APPROVAL",
      result: null, errorMessage: null, createdAt: now, updatedAt: now,
    }],
    approval: {
      id: `approval-${id}`,
      serviceId: "github",
      actionName: "create_issue",
      actionDescription: "创建 GitHub Issue",
      targetRepository: repository,
      arguments: JSON.stringify({
        title: "Document the recovery workflow",
        body: "The project notes describe the recovery workflow.\n\nSource: project-notes.txt, page 1.",
      }),
      status: completed ? "EXECUTED" : denied ? "DENIED" : "PENDING",
      decidedBy: completed || denied ? ownerId : null,
      decidedAt: completed || denied ? now : null,
      externalResourceId: completed ? issueUrl : null,
      reconciliationState: null,
      reconciliationOutcome: null,
      reconciliationReason: null,
      reconciliationEvidence: null,
      reconciliationEvidenceReference: null,
      reconciliationActorId: null,
      reconciliationAt: null,
    },
    timeline,
    modelUsage: { modelCallCount: 1, inputTokens: 120, outputTokens: 40, complete: true },
    nextAction: completed || denied ? "NONE" : "OWNER_DECISION",
  }
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(apiResponse(data)),
  })
}

async function installMockApi(page: Page) {
  let dataset: Record<string, unknown> | null = null
  let uploaded = false
  let connection: { githubUserId: string; githubLogin: string; repositories: string[] } | null = null
  let agent: Record<string, unknown> | null = null
  let session: Record<string, unknown> | null = null
  const runs: ReturnType<typeof makeRun>[] = []
  const calls: Array<{ method: string; path: string; body: unknown }> = []
  const externalRequests: string[] = []

  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ userId: "1", exp: 2540188800 }))
      .replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_")
    localStorage.setItem("auth_token", `e30.${payload}.e2e-signature`)
  })
  page.on("request", request => {
    const url = new URL(request.url())
    if (!(["localhost", "127.0.0.1"].includes(url.hostname))) externalRequests.push(request.url())
  })

  await page.route("https://fonts.googleapis.com/**", route => route.fulfill({
    status: 200,
    contentType: "text/css",
    body: "",
  }))
  await page.route("https://fonts.gstatic.com/**", route => route.abort())

  await page.route("**/api/**", async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api/, "")
    const method = request.method()
    let body: unknown = null
    if (method !== "GET" && method !== "DELETE" && request.postData()) {
      try { body = request.postDataJSON() } catch { body = "multipart" }
    }
    calls.push({ method, path, body })

    if (path === "/rag/datasets" && method === "GET") {
      await fulfill(route, { ...emptyPage(), records: dataset ? [dataset] : [], total: dataset ? 1 : 0, pages: dataset ? 1 : 0 })
      return
    }
    if (path === "/rag/datasets" && method === "POST") {
      const input = body as { name: string; description?: string }
      dataset = {
        id: datasetId, name: input.name, description: input.description || "", icon: "",
        userId: "1", fileCount: uploaded ? 1 : 0, createdAt: now, updatedAt: now,
      }
      await fulfill(route, dataset)
      return
    }
    if (path === `/rag/datasets/${datasetId}` && method === "GET") {
      await fulfill(route, dataset)
      return
    }
    if (path === "/rag/datasets/files" && method === "POST") {
      uploaded = true
      if (dataset) dataset.fileCount = 1
      await fulfill(route, {
        id: "file-e2e", url: "/mock/project-notes.txt", size: 64, filename: "project-notes.txt",
        originalFilename: "project-notes.txt", ext: "txt", contentType: "text/plain", dataSetId: datasetId,
        filePageSize: 1, isInitialize: 1, isEmbedding: 1, userId: "1", createdAt: now, updatedAt: now,
      })
      return
    }
    if (path === `/rag/datasets/${datasetId}/files` && method === "GET") {
      const file = {
        id: "file-e2e", url: "/mock/project-notes.txt", size: 64, filename: "project-notes.txt",
        originalFilename: "project-notes.txt", ext: "txt", contentType: "text/plain", dataSetId: datasetId,
        filePageSize: 1, isInitialize: 1, isEmbedding: 1, userId: "1", createdAt: now, updatedAt: now,
      }
      await fulfill(route, { ...emptyPage(), records: uploaded ? [file] : [], total: uploaded ? 1 : 0, pages: uploaded ? 1 : 0 })
      return
    }
    if (path === `/rag/datasets/${datasetId}/files/progress` && method === "GET") {
      await fulfill(route, uploaded ? [{
        fileId: "file-e2e", filename: "project-notes.txt", processingStatusEnum: "COMPLETED",
        processingStatus: 5, processingStatusDescription: "已完成", processProgress: 100,
        isInitialize: 1, isEmbedding: 1,
      }] : [])
      return
    }
    if (path === `/rag/datasets/${datasetId}/readiness` && method === "GET") {
      await fulfill(route, {
        datasetId, status: uploaded ? "READY" : "EMPTY", fileCount: uploaded ? 1 : 0,
        readyFileCount: uploaded ? 1 : 0, failedFileCount: 0, failureReason: null,
        latestReadyVersionId: uploaded ? "knowledge-version-e2e" : null,
        latestReadyVersionNumber: uploaded ? "v1" : null,
      })
      return
    }
    if (path === "/agents/knowledge-bases/available" && method === "GET") {
      await fulfill(route, dataset ? [{
        id: datasetId, name: dataset.name, description: dataset.description || "",
        fileCount: uploaded ? 1 : 0, createdAt: now, updatedAt: now,
      }] : [])
      return
    }
    if (path === "/users/github-connection") {
      if (method === "GET") {
        await fulfill(route, connection)
        return
      }
      if (method === "POST") {
        const input = body as { token: string; repositories: string[] }
        connection = { githubUserId: ownerId, githubLogin: "e2e-owner", repositories: input.repositories }
        await fulfill(route, connection)
        return
      }
    }
    if (path === "/agents" && method === "POST") {
      const input = body as Record<string, unknown>
      agent = {
        id: agentId, name: input.name, avatar: null, description: input.description,
        systemPrompt: input.systemPrompt, welcomeMessage: input.welcomeMessage,
        modelConfig: input.modelConfig, tools: [], toolIds: [],
        knowledgeBaseIds: input.knowledgeBaseIds || [], publishedVersion: null,
        enabled: true, userId: "1", createdAt: now, updatedAt: now,
      }
      await fulfill(route, agent)
      return
    }
    if (path === "/agents/workspaces/agents" && method === "GET") {
      await fulfill(route, agent ? [agent] : [])
      return
    }
    if (path === "/agents/sessions/agent-e2e" && method === "GET") {
      await fulfill(route, session ? [session] : [])
      return
    }
    if (path === "/agents/sessions/agent-e2e" && method === "POST") {
      session = {
        id: sessionId, agentId, title: "新会话", description: "", createdAt: now, updatedAt: now,
        isArchived: false,
      }
      await fulfill(route, session)
      return
    }
    if (path === `/agents/sessions/${sessionId}` && method === "PUT") {
      await fulfill(route, null)
      return
    }
    if (path === `/agents/sessions/${sessionId}/runs` && method === "GET") {
      await fulfill(route, [...runs].reverse())
      return
    }
    if (path === `/agents/sessions/${sessionId}/runs` && method === "POST") {
      const input = body as { goal: string; targetRepository: string; knowledgeBaseId: string; approvalPolicy: string }
      if (!connection?.repositories.includes(input.targetRepository)) {
        await route.fulfill({ status: 403, contentType: "application/json", body: JSON.stringify({
          code: 403, message: "Repository is outside the connected authorization scope", data: null,
        }) })
        return
      }
      const run = makeRun(`run-${runs.length + 1}`, input.goal, "WAITING_APPROVAL")
      run.approvalPolicy = input.approvalPolicy as typeof run.approvalPolicy
      runs.push(run)
      await fulfill(route, run)
      return
    }
    if (path.startsWith("/agents/runs/") && path.endsWith("/knowledge") && method === "GET") {
      await fulfill(route, {
        knowledgeVersionId: "knowledge-version-e2e", hasEvidence: true,
        message: "找到了支持目标的资料。", canPublish: true,
        citations: [{
          knowledgeVersionId: "knowledge-version-e2e", documentId: "document-e2e",
          originalDocumentId: "document-e2e", fileId: "file-e2e", fileName: "project-notes.txt",
          page: 1, content: "项目恢复流程要求记录每次外部写入。", sourceUrl: "/mock/project-notes.txt#page=1",
        }],
      })
      return
    }
    if (path.startsWith("/agents/runs/") && path.includes("/approvals/") && method === "POST") {
      const runId = path.split("/")[3]
      const run = runs.find(candidate => candidate.id === runId)
      const decision = (body as { decision: string }).decision
      const updated = makeRun(runId, run?.goal || "", decision === "APPROVE" ? "COMPLETED" : "FAILED", decision)
      const index = runs.findIndex(candidate => candidate.id === runId)
      if (index >= 0) runs[index] = updated
      await fulfill(route, updated)
      return
    }
    if (path.startsWith("/agents/runs/") && method === "GET") {
      const run = runs.find(candidate => candidate.id === path.split("/")[3])
      await fulfill(route, run || null)
      return
    }

    if (path === "/tools/installed" && method === "GET") {
      await fulfill(route, emptyPage())
      return
    }
    if (path === "/llms/models/default" && method === "GET") {
      await fulfill(route, null)
      return
    }
    if (path === "/llms/models" && method === "GET") {
      await fulfill(route, [])
      return
    }

    // Unrelated shell requests return an empty successful response; all workflow APIs above are stateful.
    const pageDataPaths = ["/tools/market", "/tools/installed", "/rag/datasets"]
    await fulfill(route, pageDataPaths.includes(path) ? emptyPage() : [])
  })

  return { calls, externalRequests }
}

test("configure an Agent, upload evidence, and approve a GitHub Issue run", async ({ page }) => {
  const { calls, externalRequests } = await installMockApi(page)

  await page.goto("/knowledge")
  await page.waitForResponse(response => response.url().includes("/api/rag/datasets")
    && response.request().method() === "GET")
  const header = page.locator("header")
  await expect(header.getByRole("link", { name: "工作区" }).first()).toBeVisible()
  await expect(header.getByRole("link", { name: "探索" })).toHaveCount(0)
  await expect(header.getByRole("link", { name: "工具市场" })).toHaveCount(0)
  await page.getByRole("button", { name: "创建数据集", exact: true }).first().click()
  await page.getByLabel("数据集名称").fill("E2E Guidance")
  await page.getByLabel("数据集说明（可选）").fill("Project recovery notes for the acceptance flow.")
  await page.getByRole("button", { name: "创建", exact: true }).click()
  await page.getByRole("link", { name: /E2E Guidance/ }).first().click()

  await page.locator("#file-upload").setInputFiles({
    name: "project-notes.txt",
    mimeType: "text/plain",
    buffer: Buffer.from("Record every external write in the project recovery workflow."),
  })
  await expect(page.getByText("project-notes.txt").first()).toBeVisible()
  await expect(page.getByText("全部 1 个文件已完成索引")).toBeVisible()
  await expect(page.getByText("可检索版本 ID：knowledge-version-e2e")).toBeVisible()

  await page.goto("/settings/github")
  await page.getByLabel("Fine-grained personal access token").fill("e2e-only-not-a-real-token")
  await page.getByLabel("允许的仓库").fill(repository)
  await page.getByRole("button", { name: "验证并保存连接" }).click()
  await expect(page.getByText("已连接：e2e-owner")).toBeVisible()

  await page.goto("/studio/new")
  await page.getByLabel("名称").fill("Issue Draft Assistant")
  await page.getByPlaceholder("输入功能性助理的描述").fill("Draft evidence-based GitHub Issues from uploaded project notes.")
  const toolsTab = page.getByRole("tab", { name: "工具 & 知识库" })
  await toolsTab.click()
  await expect(toolsTab).toHaveAttribute("aria-selected", "true")
  await expect(page.getByRole("heading", { name: "知识库", exact: true })).toBeVisible()
  const knowledgeCard = page.getByRole("heading", { name: "E2E Guidance", exact: true }).locator("..")
  await expect(knowledgeCard.getByRole("switch")).toBeVisible()
  await knowledgeCard.getByRole("switch").click()
  await page.getByRole("button", { name: "确认创建" }).click()

  await page.goto(`/workspace?id=${agentId}`)
  await page.getByRole("button", { name: "开启新会话" }).click()
  await page.getByRole("button", { name: "运行", exact: true }).click()

  const knowledgeSelect = page.locator("#run-knowledge-base")
  await expect(knowledgeSelect).toHaveValue(datasetId)
  const repositorySelect = page.locator("#run-target-repository")
  await expect(repositorySelect.locator("option")).toHaveCount(1)
  await expect(repositorySelect.locator("option").first()).toHaveText(repository)
  await page.locator("#run-approval-policy").selectOption("REQUIRE_APPROVAL_FOR_ALL_WRITES")

  await page.locator("#run-goal").fill("Create an issue from the project recovery notes.")
  await page.getByRole("button", { name: "开始运行" }).click()
  await expect(page.getByText("等待所有者审批 GitHub Issue 创建。")).toBeVisible()
  await expect(page.getByRole("button", { name: /project-notes\.txt · 第 1 页/ })).toBeVisible()
  await page.getByRole("button", { name: "批准并继续" }).click()
  await expect(page.getByRole("link", { name: issueUrl })).toBeVisible()
  await expect(page.getByText("运行已完成")).toBeVisible()
  await expect(page.getByRole("region", { name: "运行时间线" })).toBeVisible()

  await page.locator("#run-goal").fill("Create another issue but reject the external write.")
  await page.getByRole("button", { name: "开始运行" }).click()
  await expect(page.getByText("等待所有者审批 GitHub Issue 创建。")).toBeVisible()
  await page.getByRole("button", { name: "拒绝写入" }).click()
  await expect(page.getByText("所有者已拒绝此操作。")).toBeVisible()

  expect(calls.some(call => call.method === "POST" && call.path === "/rag/datasets")).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path === "/rag/datasets/files" && call.body === "multipart")).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path === "/users/github-connection"
    && (call.body as { token?: string }).token === "e2e-only-not-a-real-token")).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path === "/agents")).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path === `/agents/sessions/${sessionId}/runs`
    && (call.body as { knowledgeBaseId?: string }).knowledgeBaseId === datasetId)).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path.includes(`/agents/runs/run-1/approvals/approval-run-1`)
    && (call.body as { decision?: string }).decision === "APPROVE")).toBe(true)
  expect(calls.some(call => call.method === "POST" && call.path.includes(`/agents/runs/run-2/approvals/approval-run-2`)
    && (call.body as { decision?: string }).decision === "DENY")).toBe(true)
  expect(externalRequests.filter(url => !url.includes("fonts.googleapis.com")
    && !url.includes("fonts.gstatic.com"))).toEqual([])
})
