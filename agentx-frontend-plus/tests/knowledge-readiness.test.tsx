import { afterEach, expect, it, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { KnowledgeReadinessPanel } from "@/components/knowledge/knowledge-readiness-panel"

afterEach(() => cleanup())

it("should_explainProcessingFailureWithoutOfferingAReadyVersion", () => {
  render(<KnowledgeReadinessPanel readiness={{
    datasetId: "dataset-1", status: "FAILED", fileCount: 1, readyFileCount: 0,
    failedFileCount: 1, failureReason: "无法识别文件内容，请检查格式后重试",
    latestReadyVersionId: null, latestReadyVersionNumber: null,
  }} creating={false} onCreateVersion={vi.fn()} onOpenVersion={vi.fn()} />)

  expect(screen.getByText("无法识别文件内容，请检查格式后重试")).toBeTruthy()
  expect(screen.queryByRole("button", { name: "确认可检索版本" })).toBeNull()
  expect(screen.queryByText(/可检索版本 ID/)).toBeNull()
})

it("should_offerVersionCreationOnlyWhenAllFilesAreReady", () => {
  const onCreateVersion = vi.fn()
  render(<KnowledgeReadinessPanel readiness={{
    datasetId: "dataset-1", status: "READY", fileCount: 1, readyFileCount: 1,
    failedFileCount: 0, failureReason: null,
    latestReadyVersionId: null, latestReadyVersionNumber: null,
  }} creating={false} onCreateVersion={onCreateVersion} onOpenVersion={vi.fn()} />)

  fireEvent.click(screen.getByRole("button", { name: "确认可检索版本" }))
  expect(onCreateVersion).toHaveBeenCalledOnce()
})

it("should_showStableVersionLinkAfterConfirmation", () => {
  const onOpenVersion = vi.fn()
  render(<KnowledgeReadinessPanel readiness={{
    datasetId: "dataset-1", status: "READY", fileCount: 1, readyFileCount: 1,
    failedFileCount: 0, failureReason: null,
    latestReadyVersionId: "ready-version-123", latestReadyVersionNumber: "1.0.1",
  }} creating={false} onCreateVersion={vi.fn()} onOpenVersion={onOpenVersion} />)

  expect(screen.getByText(/ready-version-123/)).toBeTruthy()
  fireEvent.click(screen.getByRole("button", { name: "查看可检索版本" }))
  expect(onOpenVersion).toHaveBeenCalledWith("ready-version-123")
})
