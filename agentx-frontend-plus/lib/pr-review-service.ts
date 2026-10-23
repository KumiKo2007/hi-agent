import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/rag-dataset"

export interface ReviewFinding {
  severity: "P0" | "P1" | "P2" | "P3"; title: string; explanation: string
  path: string; side: "BASE" | "HEAD"; startLine: number; endLine: number
  commitSha: string; evidence: string; sourceUrl: string
  verification: "SOURCE_VERIFIED" | "UNVERIFIED"; verificationNote: string
}
export interface PrReviewContent {
  inputId: string; repository: string; pullNumber: number; baseSha: string; headSha: string
  summary: string; findings: ReviewFinding[]; unverifiedFindings: ReviewFinding[]
  gaps: string[]; testsExecuted: false; assessmentNote: string
}
export interface PrReview {
  id: string; kind: "PR_REVIEW"; inputRevisionId: string; createdAt: string; currentRevisionId: string | null
  parameters: { inputId: string; repository: string; pullNumber: number; baseSha: string; headSha: string }
  run: { id: string; modelId: string; status: "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED"; error: string | null }
  revision: { id: string; runId: string; inputRevisionId: string; content: PrReviewContent; createdAt: string } | null
}
const path = (id: string) => `/reviews/reports/${encodeURIComponent(id)}`
export const PrReviewService = {
  list: (): Promise<ApiResponse<PrReview[]>> => httpClient.get("/reviews/reports"),
  get: (id: string): Promise<ApiResponse<PrReview>> => httpClient.get(path(id)),
  create: (inputId: string, modelId: string): Promise<ApiResponse<PrReview>> => httpClient.post("/reviews/reports", { inputId, modelId }),
  generate: (id: string): Promise<ApiResponse<PrReview>> => httpClient.post(`${path(id)}/generate`, undefined, { timeout: 300_000 }),
  async markdown(id: string, revisionId: string): Promise<Blob> {
    const response = await httpClient.get<Response>(`${path(id)}/revisions/${encodeURIComponent(revisionId)}/markdown`, {}, { raw: true })
    if (!(response instanceof Response)) throw new Error("下载失败，请检查网络后重试")
    if (!response.ok) throw new Error("报告修订不存在或无访问权限")
    return response.blob()
  },
}
