import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/rag-dataset"
import type { CreateTeachingLesson, LessonRevision, TeachingLesson } from "@/types/teaching-lesson"
const path = (id: string) => `/teaching/lessons/${encodeURIComponent(id)}`
export const TeachingLessonService = {
  list: (): Promise<ApiResponse<TeachingLesson[]>> => httpClient.get("/teaching/lessons"),
  get: (id: string): Promise<ApiResponse<TeachingLesson>> => httpClient.get(path(id)),
  create: (data: CreateTeachingLesson): Promise<ApiResponse<TeachingLesson>> => httpClient.post("/teaching/lessons", data),
  generate: (id: string): Promise<ApiResponse<TeachingLesson>> => httpClient.post(`${path(id)}/generate`, undefined, { timeout: 300_000 }),
  revision: (id: string, revision: string): Promise<ApiResponse<LessonRevision>> => httpClient.get(`${path(id)}/revisions/${encodeURIComponent(revision)}`),
}
