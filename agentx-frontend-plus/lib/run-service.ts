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
  goal: string
  status: string
  result: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
  steps: RunStep[]
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
}
