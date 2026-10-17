import { afterEach, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen, within } from "@testing-library/react"
import { RunPanel } from "@/components/run-panel"
import { getValidatedGitHubIssueUrl } from "@/components/github-issue-result"

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

it("should_showACompletedRunAndRestoreItsResultAfterReload", async () => {
  const run = {
    id: "run-1", sessionId: "session-1", agentId: "agent-1",
    goal: "整理版本说明", status: "COMPLETED", result: "回答：整理版本说明",
    errorMessage: null, createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:01",
    steps: [{ id: "step-1", runId: "run-1", sequenceNo: 1, kind: "MODEL_CALL",
      status: "COMPLETED", result: "回答：整理版本说明", errorMessage: null,
      createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:01" }],
  }
  let saved = false
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs") && init?.method === "POST") {
      expect(JSON.parse(String(init.body))).toEqual({ goal: "整理版本说明", approvalPolicy: "CREATOR_DEFAULT",
        knowledgeBaseId: null, targetRepository: null })
      saved = true
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: saved ? [run] : [] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-1")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  const firstView = render(<RunPanel sessionId="session-1" />)
  await screen.findByText("暂无运行")
  fireEvent.change(screen.getByLabelText("目标"), { target: { value: "整理版本说明" } })
  fireEvent.click(screen.getByRole("button", { name: "开始运行" }))
  await screen.findByText("回答：整理版本说明")
  expect(screen.getByText("运行 run-1")).toBeTruthy()

  firstView.unmount()
  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("回答：整理版本说明")
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith("/agents/runs/run-1"))).toBe(true)
})

it("should_allowARunToTightenTheCreatorApprovalPolicy", async () => {
  const run = {
    id: "run-policy", sessionId: "session-1", agentId: "agent-1", goal: "创建工单",
    status: "COMPLETED", result: "完成", errorMessage: null,
    approvalPolicy: "REQUIRE_APPROVAL_FOR_ALL_WRITES",
    createdAt: "2026-10-17T00:00:00", updatedAt: "2026-10-17T00:00:01", steps: [],
  }
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs") && init?.method === "POST") {
      expect(JSON.parse(String(init.body))).toEqual({ goal: "创建工单",
        approvalPolicy: "REQUIRE_APPROVAL_FOR_ALL_WRITES", knowledgeBaseId: null, targetRepository: null })
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [] }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("暂无运行")
  const policy = screen.getByLabelText("外部写入审批") as HTMLSelectElement
  expect(policy.value).toBe("CREATOR_DEFAULT")
  fireEvent.change(policy, { target: { value: "REQUIRE_APPROVAL_FOR_ALL_WRITES" } })
  fireEvent.change(screen.getByLabelText("目标"), { target: { value: "创建工单" } })
  fireEvent.click(screen.getByRole("button", { name: "开始运行" }))
  await screen.findByText("当前规则：所有外部写入均需审批。")
})

it("should_refreshAPendingRunUntilItsPersistedResultIsVisible", async () => {
  const pending = {
    id: "run-2", sessionId: "session-1", agentId: "agent-1", goal: "分析项目",
    status: "PENDING", result: null, errorMessage: null,
    createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:00", steps: [],
  }
  const completed = { ...pending, status: "COMPLETED", result: "最终答案" }
  let detailCalls = 0
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs") && init?.method === "POST") {
      return new Response(JSON.stringify({ code: 200, data: pending }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [pending] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-2")) {
      detailCalls++
      return new Response(JSON.stringify({ code: 200, data: detailCalls > 1 ? completed : pending }),
        { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("待执行 · run-2")
  await screen.findByText("最终答案", {}, { timeout: 5000 })
  expect(detailCalls).toBeGreaterThan(1)
})

it("should_cancelAnOwnedPendingRunAndShowItsPersistedStatus", async () => {
  const pending = {
    id: "run-3", sessionId: "session-1", agentId: "agent-1", goal: "停止目标",
    status: "PENDING", result: null, errorMessage: null,
    createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:00", steps: [],
  }
  const cancelled = { ...pending, status: "CANCELLED", errorMessage: "运行已取消" }
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs") && (!init?.method || init.method === "GET")) {
      return new Response(JSON.stringify({ code: 200, data: [pending] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-3/cancel") && init?.method === "POST") {
      return new Response(JSON.stringify({ code: 200, data: cancelled }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-3")) {
      return new Response(JSON.stringify({ code: 200, data: pending }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("待执行 · run-3")
  fireEvent.click(screen.getByRole("button", { name: "取消运行" }))
  await screen.findByText("已取消 · run-3")
  expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith("/agents/runs/run-3/cancel")
    && init?.method === "POST")).toBe(true)
})

it("should_showAndPersistTheOwnerApprovalDecisionForFixedWriteParameters", async () => {
  const pending = {
    id: "run-approval", sessionId: "session-1", agentId: "agent-1", goal: "Create an issue",
    status: "WAITING_APPROVAL", result: null, errorMessage: null,
    createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:01", steps: [],
    approval: {
      id: "approval-1", serviceId: "github-service", actionName: "create_issue",
      actionDescription: "Create GitHub issue", targetRepository: "kumiko2007/hi-agent",
      arguments: '{"repository":"kumiko2007/hi-agent","title":"Review me"}', status: "PENDING",
      decidedBy: null, decidedAt: null, externalResourceId: null,
    },
  }
  let currentRun: Record<string, unknown> = pending
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [currentRun] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-approval/approvals/approval-1") && init?.method === "POST") {
      expect(JSON.parse(String(init.body))).toEqual({ decision: "DENY" })
      currentRun = { ...pending, status: "FAILED", errorMessage: "外部写入审批被拒绝",
        approval: { ...pending.approval, status: "DENIED", decidedBy: "owner-1" } }
      return new Response(JSON.stringify({ code: 200, data: currentRun }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-approval")) {
      return new Response(JSON.stringify({ code: 200, data: currentRun }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  const firstView = render(<RunPanel sessionId="session-1" />)
  await screen.findByText("kumiko2007/hi-agent")
  expect(screen.getByText('{"repository":"kumiko2007/hi-agent","title":"Review me"}')).toBeTruthy()
  expect(screen.getByRole("button", { name: "批准并继续" })).toBeTruthy()

  firstView.unmount()
  const refreshedPendingView = render(<RunPanel sessionId="session-1" />)
  await screen.findByText("此固定操作等待运行所有者审批。")
  expect(screen.getByText('{"repository":"kumiko2007/hi-agent","title":"Review me"}')).toBeTruthy()
  expect(screen.getByRole("button", { name: "拒绝写入" })).toBeTruthy()
  fireEvent.click(screen.getByRole("button", { name: "拒绝写入" }))
  await screen.findByText("所有者已拒绝此操作。")
  expect(screen.queryByRole("button", { name: "拒绝写入" })).toBeNull()
  expect(fetchMock.mock.calls.some(([url, init]) => String(url).endsWith(
    "/agents/runs/run-approval/approvals/approval-1") && init?.method === "POST")).toBe(true)

  refreshedPendingView.unmount()
  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("所有者已拒绝此操作。")
  expect(screen.getByText("目标仓库", { exact: true })).toBeTruthy()
})

it("should_surfaceThePersistedExternalResourceAfterAnApprovedWrite", async () => {
  const run = {
    id: "run-4", sessionId: "session-1", agentId: "agent-1", goal: "Create an issue",
    status: "WAITING_RECONCILIATION", result: null, errorMessage: null,
    createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:01", steps: [],
    approval: {
      id: "approval-4", serviceId: "github-service", actionName: "create_issue",
      actionDescription: "Create GitHub issue", targetRepository: "kumiko2007/hi-agent",
      arguments: '{"repository":"kumiko2007/hi-agent","title":"Review me"}', status: "EXECUTED",
      decidedBy: "owner-1", decidedAt: "2026-10-15T00:00:01",
      externalResourceId: "https://github.com/kumiko2007/hi-agent/issues/42",
    },
  }
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-4")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("https://github.com/kumiko2007/hi-agent/issues/42")
  expect(screen.getByText("外部资源 ID")).toBeTruthy()
  expect(screen.queryByRole("button", { name: "取消运行" })).toBeNull()
})

it("should_requireAnAuditedResolutionInsteadOfCancellationWhileAWriteIsUncertain", async () => {
  const run = {
    id: "run-reconciliation", sessionId: "session-1", agentId: "agent-1", goal: "Create an issue",
    status: "WAITING_RECONCILIATION", result: null,
    errorMessage: "外部写入结果不确定，需核对后继续",
    createdAt: "2026-10-17T00:00:00", updatedAt: "2026-10-17T00:00:01", steps: [],
    approval: {
      id: "approval-reconciliation", serviceId: "github-service", actionName: "create_issue",
      actionDescription: "Create GitHub issue", targetRepository: "kumiko2007/hi-agent",
      arguments: '{"repository":"kumiko2007/hi-agent","title":"Review me"}',
      status: "RECONCILIATION_REQUIRED", decidedBy: "owner-1", decidedAt: "2026-10-17T00:00:01",
      externalResourceId: null,
    },
  }
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-reconciliation")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("待核对 · run-reconciliation")
  expect(screen.queryByRole("button", { name: "取消运行" })).toBeNull()
  expect(screen.getByRole("button", { name: "记录已创建" })).toBeTruthy()
  expect(screen.getByRole("button", { name: "确认未创建并重试" })).toBeTruthy()
  expect(screen.getByRole("button", { name: "停止并保留记录" })).toBeTruthy()
})

it("should_showTheSafeReasonForAStoredSchemaReviewPause", async () => {
  const run = {
    id: "run-schema", sessionId: "session-1", agentId: "agent-1", goal: "Search docs",
    status: "WAITING_SCHEMA_REVIEW", result: null,
    errorMessage: "MCP 工具参数结构与运行时结构已变化，运行已暂停；请检查动作定义后重新运行。",
    createdAt: "2026-10-17T00:00:00", updatedAt: "2026-10-17T00:00:01", steps: [],
  }
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-schema")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("工具结构待核对 · run-schema")
  expect(screen.getByText(run.errorMessage)).toBeTruthy()
  expect(screen.getByRole("button", { name: "取消运行" })).toBeTruthy()
})

it("should_openAPinnedCitationThroughTheAuthenticatedRunApi", async () => {
  const run = {
    id: "run-3", sessionId: "session-1", agentId: "agent-1", knowledgeVersionId: "version-1",
    goal: "Review docs", status: "COMPLETED", result: "Done", errorMessage: null,
    createdAt: "2026-10-15T00:00:00", updatedAt: "2026-10-15T00:00:01", steps: [],
  }
  const citation = {
    knowledgeVersionId: "version-1", documentId: "doc-1", originalDocumentId: "source-1",
    fileId: "file-1", fileName: "guide.md", page: 2, content: "Excerpt", sourceUrl: "/rag/datasets/x",
  }
  const fetchMock = vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-3")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    if (url.includes("/agents/runs/run-3/knowledge?")) {
      return new Response(JSON.stringify({ code: 200, data: {
        knowledgeVersionId: "version-1", hasEvidence: true, canPublish: true,
        message: "Found evidence", citations: [citation],
      } }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-3/knowledge/documents/doc-1")) {
      return new Response(JSON.stringify({ code: 200, data: { ...citation, content: "Full pinned source" } }),
        { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  render(<RunPanel sessionId="session-1" />)
  fireEvent.click(await screen.findByRole("button", { name: "guide.md · 第 2 页" }))
  await screen.findByText("Full pinned source")
  expect(fetchMock.mock.calls.some(([url]) => String(url).endsWith(
    "/agents/runs/run-3/knowledge/documents/doc-1"))).toBe(true)
})

it("should_submitTheChosenKnowledgeBaseAndAuthorizedRepository", async () => {
  const pending = {
    id: "run-configured", sessionId: "session-1", agentId: "agent-1", goal: "Create an issue",
    status: "PENDING", result: null, errorMessage: null, createdAt: "2026-10-17T00:00:00",
    updatedAt: "2026-10-17T00:00:00", steps: [],
  }
  let submitted: unknown
  const fetchMock = vi.fn(async (input: string | URL | Request, init?: RequestInit) => {
    const url = String(input)
    if (url.endsWith("/users/github-connection")) {
      return new Response(JSON.stringify({ code: 200, data: {
        githubUserId: "user-1", githubLogin: "kumiko2007", repositories: ["kumiko2007/hi-agent", "octo/other"],
      } }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs") && init?.method === "POST") {
      submitted = JSON.parse(String(init.body))
      return new Response(JSON.stringify({ code: 200, data: pending }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-configured")) {
      return new Response(JSON.stringify({ code: 200, data: pending }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  })
  vi.stubGlobal("fetch", fetchMock)

  render(<RunPanel sessionId="session-1" agentName="Issue Agent" knowledgeBaseIds={["kb-1", "kb-2"]} />)
  await screen.findByText(content => content.includes("Agent：") && content.includes("Issue Agent"))
  const knowledge = screen.getByLabelText("知识库") as HTMLSelectElement
  fireEvent.change(knowledge, { target: { value: "kb-2" } })
  const repository = await screen.findByLabelText("GitHub 目标仓库") as HTMLSelectElement
  fireEvent.change(repository, { target: { value: "octo/other" } })
  fireEvent.change(screen.getByLabelText("目标"), { target: { value: "Create an issue" } })
  fireEvent.click(screen.getByRole("button", { name: "开始运行" }))

  await screen.findByText("待执行 · run-configured")
  expect(submitted).toEqual({ goal: "Create an issue", approvalPolicy: "CREATOR_DEFAULT",
    knowledgeBaseId: "kb-2", targetRepository: "octo/other" })
})

it("should_showChronologicalSafeTimelineUsageAndNextAction", async () => {
  const emptyDetails = {
    failureCode: null, modelName: null, inputTokens: null, outputTokens: null, usageStatus: null,
    durationMs: null, knowledgeVersionId: null, sourceDocumentId: null, sourceFileId: null,
    sourceName: null, sourcePage: null, sourceUrl: null, serviceId: null, actionName: null,
    targetRepository: null, actorId: null, decision: null, resultReference: null,
  }
  const run = {
    id: "run-timeline", sessionId: "session-1", agentId: "agent-1", goal: "Review source",
    status: "FAILED", result: null, errorMessage: "Run failed", approvalPolicy: "CREATOR_DEFAULT",
    createdAt: "2026-10-17T00:00:00", updatedAt: "2026-10-17T00:00:03", steps: [],
    nextAction: "CHECK_REPOSITORY_AUTHORIZATION",
    modelUsage: { modelCallCount: 1, inputTokens: 21, outputTokens: 8, complete: false },
    timeline: [
      { id: "event-complete", runId: "run-timeline", stepId: "step-1", eventType: "RUN_FAILED",
        status: "FAILED", summary: "运行失败", details: { ...emptyDetails, failureCode: "PERMISSION_DENIED" },
        createdAt: "2026-10-17T00:00:03" },
      { id: "event-created", runId: "run-timeline", stepId: "step-1", eventType: "RUN_CREATED",
        status: "PENDING", summary: "运行已创建", details: emptyDetails, createdAt: "2026-10-17T00:00:00" },
      { id: "event-model", runId: "run-timeline", stepId: "step-1", eventType: "MODEL_CALL",
        status: "COMPLETED", summary: "模型调用完成", details: { ...emptyDetails, modelName: "safe model",
          inputTokens: 21, outputTokens: 8, usageStatus: "AVAILABLE", durationMs: 240 },
        createdAt: "2026-10-17T00:00:01" },
    ],
    approval: {
      id: "approval-1", serviceId: "github", actionName: "create_issue", actionDescription: "Create issue",
      targetRepository: "octo/project",
      arguments: '{"repository":"octo/project","title":"Review this","body":"See [S1]","api_key":"[REDACTED]"}',
      status: "DENIED", decidedBy: "owner", decidedAt: "2026-10-17T00:00:02", externalResourceId: null,
    },
  }
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/users/github-connection")) {
      return new Response(JSON.stringify({ code: 200, data: { repositories: [] } }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-timeline")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  await screen.findByText("运行已创建", { selector: "span" })
  const eventList = within(screen.getByRole("list", { name: "事件" }))
  const events = eventList.getAllByRole("listitem")
  expect(events).toHaveLength(3)
  expect(events[0].textContent).toContain("运行已创建")
  expect(events[1].textContent).toContain("模型调用")
  expect(events[2].textContent).toContain("运行失败")
  expect(screen.getByText("模型调用 1 次 · 输入 21 / 输出 8 token · 部分调用未提供用量")).toBeTruthy()
  expect(screen.getByText("请检查目标仓库的授权和连接权限。")).toBeTruthy()
  expect(screen.getByText('{"repository":"octo/project","title":"Review this","body":"See [S1]","api_key":"[REDACTED]"}'))
    .toBeTruthy()
  expect(screen.queryByText(/abcdefghijklmnopqrstuvwxyz123456/)).toBeNull()
})

it("should_showTheCitedImmutableDraftAndLinkOnlyItsApprovedGitHubIssue", async () => {
  const issueUrl = "https://github.com/kumiko2007/hi-agent/issues/42"
  const run = {
    id: "run-cited-issue", sessionId: "session-1", agentId: "agent-1", goal: "Create an issue",
    status: "COMPLETED", result: issueUrl, errorMessage: null,
    createdAt: "2026-10-17T00:00:00", updatedAt: "2026-10-17T00:00:01", steps: [],
    approval: {
      id: "approval-cited-issue", serviceId: "github-service", actionName: "create_issue",
      actionDescription: "Create GitHub issue", targetRepository: "kumiko2007/hi-agent",
      arguments: JSON.stringify({ owner: "kumiko2007", repository: "hi-agent", title: "Review me",
        body: "Draft text\n\n---\n\n### Sources\n- [S1] `release-notes.md`, page 2\n\n<!-- hi-agent-action-intent:approval-1 -->" }),
      status: "EXECUTED", decidedBy: "owner-1", decidedAt: "2026-10-17T00:00:01", externalResourceId: issueUrl,
    },
  }
  vi.stubGlobal("fetch", vi.fn(async (input: string | URL | Request) => {
    const url = String(input)
    if (url.endsWith("/users/github-connection")) {
      return new Response(JSON.stringify({ code: 200, data: null }), { status: 200 })
    }
    if (url.endsWith("/agents/sessions/session-1/runs")) {
      return new Response(JSON.stringify({ code: 200, data: [run] }), { status: 200 })
    }
    if (url.endsWith("/agents/runs/run-cited-issue")) {
      return new Response(JSON.stringify({ code: 200, data: run }), { status: 200 })
    }
    throw new Error(`意外的请求：${url}`)
  }))

  render(<RunPanel sessionId="session-1" />)
  const link = await screen.findByRole("link", { name: issueUrl })
  expect(link.getAttribute("href")).toBe(issueUrl)
  expect(screen.getByText(content => content.includes("[S1] `release-notes.md`, page 2"))).toBeTruthy()
  expect(screen.getByText("正文中的 HTML 注释标记不会显示在 GitHub Issue 页面，用于将这次批准与结果核对关联。"))
    .toBeTruthy()
})

it("should_keepUnknownOrMismatchedMcpResourceIdsAsPlainText", () => {
  expect(getValidatedGitHubIssueUrl("https://github.com/octo/repo/issues/12", "octo/repo"))
    .toBe("https://github.com/octo/repo/issues/12")
  for (const value of [
    "mcp://github/issues/12",
    "https://github.com.evil.example/octo/repo/issues/12",
    "http://github.com/octo/repo/issues/12",
    "https://github.com/other/repo/issues/12",
    "https://github.com/octo/repo/issues/12?redirect=evil.example",
  ]) {
    expect(getValidatedGitHubIssueUrl(value, "octo/repo")).toBeNull()
  }
})
