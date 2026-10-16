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

export interface AgentRun {
  id: string
  sessionId: string
  agentId: string
  knowledgeVersionId: string | null
  goal: string
  status: string
  result: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  steps: RunStep[]
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

  static start(sessionId: string, goal: string): Promise<ApiResponse<AgentRun>> {
    return httpClient.post(`/agents/sessions/${encodeURIComponent(sessionId)}/runs`, { goal })
  }

  static cancel(runId: string): Promise<ApiResponse<AgentRun>> {
    return httpClient.post(`/agents/runs/${encodeURIComponent(runId)}/cancel`)
  }

  static knowledge(runId: string, query: string): Promise<ApiResponse<KnowledgeRetrievalResult>> {
    return httpClient.get(`/agents/runs/${encodeURIComponent(runId)}/knowledge?query=${encodeURIComponent(query)}`)
  }

  static citation(runId: string, documentId: string): Promise<ApiResponse<KnowledgeCitation>> {
    return httpClient.get(`/agents/runs/${encodeURIComponent(runId)}/knowledge/documents/${encodeURIComponent(documentId)}`)
  }
}
