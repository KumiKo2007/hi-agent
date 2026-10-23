import { httpClient } from "@/lib/http-client"
import type { ApiResponse } from "@/types/rag-dataset"

export interface SourceReview { note: string; reviewedAt: string; contentHash: string }
export interface OcrPage {
  sourceId: string | null
  physicalPage: number
  content: string | null
  coverageWarnings: string[]
  status: "MISSING" | "FORMULA_GAP" | "REVIEW_REQUIRED" | "HUMAN_REVIEWED"
  review: SourceReview | null
}
export interface OcrFile {
  versionId: string | null
  fileId: string
  fileName: string
  pageCount: number
  processingStatus: number | null
  processingError: string | null
  pages: OcrPage[]
}
function path(fileId: string, versionId?: string) {
  return `/teaching/ocr/${versionId ? `versions/${encodeURIComponent(versionId)}/` : ""}files/${encodeURIComponent(fileId)}`
}
export class TeachingOcrService {
  static get(fileId: string, versionId?: string): Promise<ApiResponse<OcrFile>> {
    return httpClient.get(path(fileId, versionId))
  }
  static image(fileId: string, page: number, versionId?: string): Promise<ApiResponse<string>> {
    return httpClient.get(`${path(fileId, versionId)}/pages/${page}/image`)
  }
  static review(versionId: string, fileId: string, page: number, note: string): Promise<ApiResponse<OcrPage>> {
    return httpClient.post(`${path(fileId, versionId)}/pages/${page}/reviews`, { note })
  }
}
