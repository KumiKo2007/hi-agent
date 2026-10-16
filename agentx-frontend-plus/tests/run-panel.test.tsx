import { afterEach, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { RunPanel } from "@/components/run-panel"

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
      expect(JSON.parse(String(init.body))).toEqual({ goal: "整理版本说明" })
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
