import { afterEach, beforeEach, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { RunReconciliationPanel } from "@/components/run-reconciliation-panel"
import { RunService } from "@/lib/run-service"
import type { AgentRun } from "@/lib/run-service"

vi.mock("@/lib/run-service", () => ({
  RunService: {
    resolveReconciliation: vi.fn(),
  },
}))

const unresolvedRun: AgentRun = {
  id: "run-unknown",
  sessionId: "session-1",
  agentId: "agent-1",
  knowledgeVersionId: null,
  goal: "Create an issue",
  status: "WAITING_RECONCILIATION",
  result: null,
  errorMessage: "外部写入结果不确定；不会重发写入。",
  createdAt: "2026-10-17T00:00:00",
  updatedAt: "2026-10-17T00:00:01",
  approvalPolicy: "REQUIRE_APPROVAL_FOR_ALL_WRITES",
  steps: [],
  approval: {
    id: "approval-1",
    serviceId: "github-service",
    actionName: "create_issue",
    actionDescription: "Create GitHub issue",
    targetRepository: "kumiko2007/hi-agent",
    arguments: '{"repository":"kumiko2007/hi-agent","title":"Review me"}',
    status: "RECONCILIATION_REQUIRED",
    decidedBy: "owner-1",
    decidedAt: "2026-10-17T00:00:01",
    externalResourceId: null,
    reconciliationState: "UNKNOWN",
    reconciliationOutcome: "UNKNOWN",
    reconciliationReason: "搜索未找到匹配项，不能证明写入未发生。",
    reconciliationEvidence: "搜索未找到匹配项，不能证明写入未发生。",
    reconciliationEvidenceReference: null,
    reconciliationActorId: null,
    reconciliationAt: "2026-10-17T00:00:02",
  },
}

const resolvedRun: AgentRun = {
  ...unresolvedRun,
  status: "PENDING",
  errorMessage: null,
  approval: {
    ...unresolvedRun.approval!,
    status: "APPROVED",
    reconciliationOutcome: "CONFIRM_NOT_CREATED",
    reconciliationEvidenceReference: "delivery-log:record-52",
    reconciliationActorId: "owner-1",
  },
}

beforeEach(() => {
  vi.mocked(RunService.resolveReconciliation).mockReset()
  vi.mocked(RunService.resolveReconciliation).mockResolvedValue({
    code: 200, message: "OK", data: resolvedRun, timestamp: 1792185600000,
  })
})

afterEach(cleanup)

it.each([
  {
    outcome: "RECORD_CREATED" as const,
    buttonName: "记录已创建",
    resourceId: "https://github.com/kumiko2007/hi-agent/issues/42",
  },
  { outcome: "CONFIRM_NOT_CREATED" as const, buttonName: "确认未创建并重试" },
  { outcome: "STOP" as const, buttonName: "停止并保留记录" },
])("should_requireEvidenceAndDispatchTheOwner_$outcome_resolution", async ({ outcome, buttonName, resourceId }) => {
  const onResolved = vi.fn()
  render(<RunReconciliationPanel run={unresolvedRun} onResolved={onResolved} />)

  const action = screen.getByRole("button", { name: buttonName }) as HTMLButtonElement
  expect(action.disabled).toBe(true)
  fireEvent.change(screen.getByLabelText("核对依据或停止原因（必填，最多 2,000 字）"), {
    target: { value: "已通过目标仓库活动记录核实" },
  })
  if (resourceId) {
    expect(action.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("已创建资源 ID 或本仓库 Issue URL"), {
      target: { value: resourceId },
    })
  }

  const evidenceReference = outcome === "CONFIRM_NOT_CREATED" ? "delivery-log:record-52" : undefined
  if (outcome === "CONFIRM_NOT_CREATED") {
    expect(action.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("未创建依据引用（确认未创建时必填，最多 512 字）"), {
      target: { value: evidenceReference },
    })
  }

  expect(screen.getByText("一次 GitHub 搜索没有找到匹配项，不能证明 Issue 未创建。确认未创建并重试时必须提供可核验的依据引用，例如服务商投递记录 ID 或本仓库的 Issue URL。")).toBeTruthy()
  expect(action.disabled).toBe(false)
  fireEvent.click(action)

  await waitFor(() => expect(RunService.resolveReconciliation).toHaveBeenCalledWith(
    "run-unknown",
    "approval-1",
    outcome,
    "已通过目标仓库活动记录核实",
    evidenceReference,
    resourceId,
  ))
  expect(onResolved).toHaveBeenCalledWith(resolvedRun)
})
