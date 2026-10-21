import { httpClient } from "@/lib/http-client"
import type { GitHubConnection } from "@/lib/github-connection-service"

export interface PrInputChoice { agentId: string; agentName: string; serviceId: string }
export interface PrInputSnapshot {
  id: string
  agentId: string
  serviceId: string
  repository: string
  pullNumber: number
  pullRequestUrl: string
  baseSha: string
  headSha: string
  title: string
  body: string
  files: { path: string; previousPath: string | null; status: string; patch: string | null }[]
  contexts: { repository: string; path: string; commitSha: string; content: string }[]
  gaps: string[]
  createdAt: string
}
interface Response<T> { code: number; message: string; data: T }
export const prInputService = {
  options: () => httpClient.get<Response<{ connection: GitHubConnection | null; choices: PrInputChoice[] }>>("/reviews/input-options"),
  create: (request: { agentId: string; serviceId: string; pullRequestUrl: string }) =>
    httpClient.post<Response<PrInputSnapshot>>("/reviews/inputs", request, { timeout: 240_000 }),
  get: (id: string) => httpClient.get<Response<PrInputSnapshot>>(`/reviews/inputs/${encodeURIComponent(id)}`),
}
