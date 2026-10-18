import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/agent"

export interface RunStep {
  id: string
  runId: string
  sequenceNo: number
  kind: string
  status: string
  result: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

export interface RunApproval {
  id: string
  serviceId: string
  actionName: string
  actionDescription: string | null
  targetRepository: string
  arguments: string
  status: string
  decidedBy: string | null
  decidedAt: string | null
  externalResourceId: string | null
  reconciliationState: string | null
  reconciliationOutcome: string | null
  reconciliationReason: string | null
  reconciliationEvidence: string | null
  reconciliationEvidenceReference: string | null
  reconciliationActorId: string | null
  reconciliationAt: string | null
}

export interface RunTimelineEventDetails {
  failureCode: string | null
  modelName: string | null
  inputTokens: number | null
  outputTokens: number | null
  usageStatus: "AVAILABLE" | "UNAVAILABLE" | null
  durationMs: number | null
  knowledgeVersionId: string | null
  sourceDocumentId: string | null
  sourceFileId: string | null
  sourceName: string | null
  sourcePage: number | null
  sourceUrl: string | null
  serviceId: string | null
  actionName: string | null
  targetRepository: string | null
  actorId: string | null
  decision: string | null
  resultReference: string | null
}

export interface RunTimelineEvent {
  id: string
  runId: string
  stepId: string | null
  eventType: string
  status: string
  summary: string
  details: RunTimelineEventDetails
  createdAt: string
}

export interface RunModelUsage {
  modelCallCount: number
  inputTokens: number
  outputTokens: number
  complete: boolean
}

export type RunNextAction =
  | "OWNER_DECISION"
  | "REVIEW_TOOL_SCHEMA"
  | "RECONCILE_EXTERNAL_WRITE"
  | "ADD_OR_SELECT_EVIDENCE"
  | "CHECK_REPOSITORY_AUTHORIZATION"
  | "CHECK_MODEL_CONNECTION"
  | "START_NEW_RUN"
  | "WAIT"
  | "NONE"

export interface AgentRun {
  id: string
  sessionId: string
  agentId: string
  knowledgeVersionId: string | null
  targetRepository: string | null
  goal: string
  status: string
  result: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  approvalPolicy: "CREATOR_DEFAULT" | "REQUIRE_APPROVAL_FOR_ALL_WRITES"
  steps: RunStep[]
  approval: RunApproval | null
  timeline?: RunTimelineEvent[]
  modelUsage?: RunModelUsage
  nextAction?: RunNextAction
}

export interface KnowledgeCitation {
  knowledgeVersionId: string
  documentId: string
  originalDocumentId: string | null
  fileId: string | null
  fileName: string | null
  page: number | null
  content: string
  sourceUrl: string
}

export interface KnowledgeRetrievalResult {
  knowledgeVersionId: string
  hasEvidence: boolean
  message: string
  canPublish: boolean
  citations: KnowledgeCitation[]
}

export class RunService {
  static list(sessionId: string): Promise<ApiResponse<AgentRun[]>> {
    return httpClient.get(`/agents/sessions/${encodeURIComponent(sessionId)}/runs`)
  }

  static detail(runId: string): Promise<ApiResponse<AgentRun>> {
    return httpClient.get(`/agents/runs/${encodeURIComponent(runId)}`)
  }

  static start(sessionId: string, goal: string,
    approvalPolicy: AgentRun["approvalPolicy"] = "CREATOR_DEFAULT",
    knowledgeBaseId: string | null = null, targetRepository: string | null = null): Promise<ApiResponse<AgentRun>> {
    return httpClient.post(`/agents/sessions/${encodeURIComponent(sessionId)}/runs`, {
      goal, approvalPolicy, knowledgeBaseId, targetRepository,
    })
  }

  static cancel(runId: string): Promise<ApiResponse<AgentRun>> {
    return httpClient.post(`/agents/runs/${encodeURIComponent(runId)}/cancel`)
  }

  static decideApproval(runId: string, approvalId: string, decision: "APPROVE" | "DENY"):
    Promise<ApiResponse<AgentRun>> {
    return httpClient.post(
      `/agents/runs/${encodeURIComponent(runId)}/approvals/${encodeURIComponent(approvalId)}`,
      { decision },
    )
  }

  static resolveReconciliation(runId: string, approvalId: string,
    outcome: "RECORD_CREATED" | "CONFIRM_NOT_CREATED" | "STOP", evidence: string,
    evidenceReference?: string, externalResourceId?: string): Promise<ApiResponse<AgentRun>> {
    return httpClient.post(
      `/agents/runs/${encodeURIComponent(runId)}/approvals/${encodeURIComponent(approvalId)}/reconciliation`,
      { outcome, evidence, evidenceReference, externalResourceId },
    )
  }

  static knowledge(runId: string, query: string): Promise<ApiResponse<KnowledgeRetrievalResult>> {
    return httpClient.get(`/agents/runs/${encodeURIComponent(runId)}/knowledge?query=${encodeURIComponent(query)}`)
  }

  static citation(runId: string, documentId: string): Promise<ApiResponse<KnowledgeCitation>> {
    return httpClient.get(`/agents/runs/${encodeURIComponent(runId)}/knowledge/documents/${encodeURIComponent(documentId)}`)
  }
}
