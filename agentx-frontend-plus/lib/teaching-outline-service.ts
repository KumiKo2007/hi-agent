import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/rag-dataset"
import type { CreateTeachingOutline, OutlineLesson, TeachingOutline } from "@/types/teaching-outline"
const path = (id: string) => `/teaching/outlines/${encodeURIComponent(id)}`
export const TeachingOutlineService = {
  list: (): Promise<ApiResponse<TeachingOutline[]>> => httpClient.get("/teaching/outlines"),
  get: (id: string): Promise<ApiResponse<TeachingOutline>> => httpClient.get(path(id)),
  create: (data: CreateTeachingOutline): Promise<ApiResponse<TeachingOutline>> => httpClient.post("/teaching/outlines", data),
  generate: (id: string): Promise<ApiResponse<TeachingOutline>> => httpClient.post(`${path(id)}/generate`, undefined, { timeout: 300_000 }),
  edit: (id: string, expectedRevisionId: string, lessons: OutlineLesson[]): Promise<ApiResponse<TeachingOutline>> => httpClient.put(path(id), { expectedRevisionId, lessons }),
  confirm: (id: string, expectedRevisionId: string): Promise<ApiResponse<TeachingOutline>> => httpClient.post(`${path(id)}/confirm`, { expectedRevisionId }),
}
