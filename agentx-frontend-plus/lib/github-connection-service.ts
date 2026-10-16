import { httpClient } from "@/lib/http-client"

export interface GitHubConnection {
  githubUserId: string
  githubLogin: string
  repositories: string[]
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const ENDPOINT = "/users/github-connection"

export const githubConnectionService = {
  get: () => httpClient.get<ApiResponse<GitHubConnection | null>>(ENDPOINT),
  connect: (token: string, repositories: string[]) =>
    httpClient.post<ApiResponse<GitHubConnection>>(ENDPOINT, { token, repositories }),
  narrowRepositories: (repositories: string[]) =>
    httpClient.put<ApiResponse<GitHubConnection>>(`${ENDPOINT}/repositories`, { repositories }),
  revoke: () => httpClient.delete<ApiResponse<null>>(ENDPOINT),
}
