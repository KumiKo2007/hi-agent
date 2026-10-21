import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/rag-dataset"
import type { TeachingSelection, TeachingSource, SaveTeachingSelection } from "@/types/teaching"

export class TeachingService {
  static list(): Promise<ApiResponse<TeachingSelection[]>> {
    return httpClient.get("/teaching/selections")
  }
  static get(id: string): Promise<ApiResponse<TeachingSelection>> {
    return httpClient.get(`/teaching/selections/${encodeURIComponent(id)}`)
  }
  static save(request: SaveTeachingSelection): Promise<ApiResponse<TeachingSelection>> {
    return httpClient.post("/teaching/selections", request)
  }
  static source(selectionId: string, sourceId: string): Promise<ApiResponse<TeachingSource>> {
    return httpClient.get(`/teaching/selections/${encodeURIComponent(selectionId)}/sources/${encodeURIComponent(sourceId)}`)
  }
}
